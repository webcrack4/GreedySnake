/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.snake;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

import org.apache.http.util.EncodingUtils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

/**
 * SnakeView: implementation of a simple game of Snake
 * 首先。这个类继承的是TileView。说明整个游戏的画面最后都能通过tile这个元素来表达。
 * 也就是所有的东西都可以通过tile来提现。这个就是整个工程的核心思想。
 */
public class SnakeView extends TileView implements OnClickListener {

	private static final String TAG = "SnakeView";

	/**
	 * Current mode of application: READY to run, RUNNING, or you have already
	 * lost. static final ints are used instead of an enum for performance
	 * reasons.
	 */
	private int mMode = READY;
	public static final int PAUSE = 0;
	public static final int READY = 1;
	public static final int RUNNING = 2;
	public static final int LOSE = 3;
	public static final int ABOUT = 4;
	public static final int RANK = 5;

	/**
	 * Current direction the snake is headed.
	 */
	private int mDirection = NORTH; // 蛇的四种方向和下一步前进的方向
	private int mNextDirection = NORTH;
	private static final int NORTH = 1;
	private static final int SOUTH = 2;
	private static final int EAST = 3;
	private static final int WEST = 4;

	/**
	 * Labels for the drawables that will be loaded into the TileView class
	 */
	private static final int RED_STAR = 1;// 这三个标签分别来表示不同的tile的drawable。比如RED_STAR代表的是蛇的身子的点（tile）
	private static final int YELLOW_STAR = 2;
	private static final int GREEN_STAR = 3;

	/**
	 * mScore: used to track the number of apples captured mMoveDelay: number of
	 * milliseconds between snake movements. This will decrease as apples are
	 * captured.
	 */
	private long mScore = 0; // 成绩，吃了多少的苹果
	private long mMoveDelay = 200;// 间隔多少毫秒进行移动一次
	/**
	 * mLastMove: tracks the absolute time when the snake last moved, and is
	 * used to determine if a move should be made based on mMoveDelay.
	 */
	private long mLastMove; // 上一次移动的时刻

	/**
	 * mStatusText: text shows to the user in some run states
	 */
	private TextView mStatusText;// 这个是开始的时候的提示语
	
	private Chronometer chronometer;
	
	private EditText theNumberOfApple;

	/**
	 * mSnakeTrail: a list of Coordinates that make up the snake's body
	 * mAppleList: the secret location of the juicy apples the snake craves.
	 */
	private ArrayList<Coordinate> mSnakeTrail = new ArrayList<Coordinate>();// 蛇的所有（点）tile的坐标数组
	private ArrayList<Coordinate> mAppleList = new ArrayList<Coordinate>();// 苹果的所有（点）tile的坐标数组

	/**
	 * Everyone needs a little randomness in their life
	 */
	private static final Random RNG = new Random();// 随机数

	/**
	 * Create a simple handler that we can use to cause animation to happen. We
	 * set ourselves as a target and we can use the sleep() function to cause an
	 * update/invalidate to occur at a later date.
	 */
	private RefreshHandler mRedrawHandler = new RefreshHandler();
	// 重点说下这边吧。其实整个工程最精华的地方。除了上面说的。就是这里了。这个刷新的handler。（如果不明白Handler的童鞋。强烈建议读源码。）
	// 通过这个Handler给自己发送消息。比如延迟30秒给自己发一个消息。这样就实现了一个循环。很聪明的想法。

	private Button mStart; // 下面的五个按钮分别是我加的。因为原来的只能响应上下左右键的操作。这边是满足触屏的操作效果。可以在布局文件中看相关的布局。

	private Button mLeft;

	private Button mRight;

	private Button mTop;

	private Button mBottom;
	
	private Button about;
	
	private Button share;

	private Spinner difficulty ;
	

	private Button CallMe;
	
	private SharedPreferences sp;
	private SharedPreferences.Editor spe;

	// 下面整个工程巧妙的地方了。调用了sleep。然后延迟delayMillis秒之后，发送自己一个消息。然后这个消息在handleMessage中被处理了。
	// 处理的过程，调用了update函数，update函数又调用了sleep函数。这样一个完美的循环就开始了。
	class RefreshHandler extends Handler {

		@Override
		public void handleMessage(Message msg) {
			SnakeView.this.update();
			SnakeView.this.invalidate();
		}

		public void sleep(long delayMillis) {
			this.removeMessages(0);
			sendMessageDelayed(obtainMessage(0), delayMillis);
		}
	};

	/**
	 * Constructs a SnakeView based on inflation from XML
	 * 
	 * @param context
	 * @param attrs
	 */
	public SnakeView(Context context, AttributeSet attrs) {
		super(context, attrs, 30);
		initSnakeView();
	}

	public SnakeView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle, 30);
		initSnakeView();
	}

	private void initSnakeView() {
		setFocusable(true);

		Resources r = this.getContext().getResources();

		// 设置了tile的类型有四种
		resetTiles(4);
		loadTile(RED_STAR, r.getDrawable(R.drawable.redstar));
		loadTile(YELLOW_STAR, r.getDrawable(R.drawable.yellowstar));
		loadTile(GREEN_STAR, r.getDrawable(R.drawable.greenstar));

		sp = this.getContext().getSharedPreferences("testsp", Context.MODE_PRIVATE);
		spe = sp.edit();
		if(-1 == sp.getInt("totalGames", -1)){
			spe.putInt("total games", 0);
			spe.putLong("No.1", 0);
			spe.putLong("No.2", 0);
			spe.putLong("No.3", 0);
			spe.commit();
		}
		
	}

	private void initNewGame() {
		mSnakeTrail.clear();
		mAppleList.clear();

		
		// For now we're just going to load up a short default eastbound snake
		// that's just turned north
		// 初始化蛇的坐标
		mSnakeTrail.add(new Coordinate(7, 30));
		mSnakeTrail.add(new Coordinate(6, 30));
		mSnakeTrail.add(new Coordinate(5, 30));
		mSnakeTrail.add(new Coordinate(4, 30));
		mSnakeTrail.add(new Coordinate(3, 30));
		mSnakeTrail.add(new Coordinate(2, 30));
		// 蛇移动的方向
		mNextDirection = NORTH;

		// Two apples to start with
		// 增加两个随机的苹果
		int i = Integer.parseInt(theNumberOfApple.getText().toString());
		if(i!=0){
			for(int j=0;j<i;j++){
			addRandomApple();
			}
		}
		
		mScore = 0;
		
	}

	/**
	 * Given a ArrayList of coordinates, we need to flatten them into an array
	 * of ints before we can stuff them into a map for flattening and storage.
	 * 
	 * @param cvec
	 *            : a ArrayList of Coordinate objects
	 * @return : a simple array containing the x/y values of the coordinates as
	 *         [x1,y1,x2,y2,x3,y3...] 这是一个坐标的数组转化成一维数组的函数。还有一个函数是相反的。
	 */
	private int[] coordArrayListToArray(ArrayList<Coordinate> cvec) {
		int count = cvec.size();
		int[] rawArray = new int[count * 2];
		for (int index = 0; index < count; index++) {
			Coordinate c = cvec.get(index);
			rawArray[2 * index] = c.x;
			rawArray[2 * index + 1] = c.y;
		}
		return rawArray;
	}

	/**
	 * Save game state so that the user does not lose anything if the game
	 * process is killed while we are in the background.
	 * 
	 * @return a Bundle with this view's state
	 */
	public Bundle saveState() {
		Bundle map = new Bundle();

		map.putIntArray("mAppleList", coordArrayListToArray(mAppleList));
		map.putInt("mDirection", Integer.valueOf(mDirection));
		map.putInt("mNextDirection", Integer.valueOf(mNextDirection));
		map.putLong("mMoveDelay", Long.valueOf(mMoveDelay));
		map.putLong("mScore", Long.valueOf(mScore));
		map.putIntArray("mSnakeTrail", coordArrayListToArray(mSnakeTrail));

		return map;
	}

	/**
	 * Given a flattened array of ordinate pairs, we reconstitute them into a
	 * ArrayList of Coordinate objects
	 * 
	 * @param rawArray
	 *            : [x1,y1,x2,y2,...]
	 * @return a ArrayList of Coordinates
	 */
	private ArrayList<Coordinate> coordArrayToArrayList(int[] rawArray) {
		ArrayList<Coordinate> coordArrayList = new ArrayList<Coordinate>();

		int coordCount = rawArray.length;
		for (int index = 0; index < coordCount; index += 2) {
			Coordinate c = new Coordinate(rawArray[index], rawArray[index + 1]);
			coordArrayList.add(c);
		}
		return coordArrayList;
	}

	/**
	 * Restore game state if our process is being relaunched
	 * 
	 * @param icicle
	 *            a Bundle containing the game state
	 *            储存游戏的数据。比如游戏中。按了home切出去了。这样就可以保存游戏的数据。切回来时就能继续。
	 */
	public void restoreState(Bundle icicle) {
		setMode(PAUSE);

		mAppleList = coordArrayToArrayList(icicle.getIntArray("mAppleList"));
		mDirection = icicle.getInt("mDirection");
		mNextDirection = icicle.getInt("mNextDirection");
		mMoveDelay = icicle.getLong("mMoveDelay");
		mScore = icicle.getLong("mScore");
		mSnakeTrail = coordArrayToArrayList(icicle.getIntArray("mSnakeTrail"));
	}

	/*
	 * handles key events in the game. Update the direction our snake is
	 * traveling based on the DPAD. Ignore events that would cause the snake to
	 * immediately turn back on itself.
	 * 
	 * (non-Javadoc)
	 * 
	 * @see android.view.View#onKeyDown(int, android.os.KeyEvent)
	 */
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent msg) {

		if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
			if (mMode == READY | mMode == LOSE) {
				/*
				 * At the beginning of the game, or the end of a previous one,
				 * we should start a new game.
				 */
				initNewGame();
				setMode(RUNNING);
				update();
				return (true);
			}

			if (mMode == PAUSE) {
				/*
				 * If the game is merely paused, we should just continue where
				 * we left off.
				 */
				setMode(RUNNING);
				update();
				return (true);
			}

			if (mDirection != SOUTH) {
				mNextDirection = NORTH;
			}
			return (true);
		}

		if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
			if (mDirection != NORTH) {
				mNextDirection = SOUTH;
			}
			return (true);
		}

		if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
			if (mDirection != EAST) {
				mNextDirection = WEST;
			}
			return (true);
		}

		if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
			if (mDirection != WEST) {
				mNextDirection = EAST;
			}
			return (true);
		}

		return super.onKeyDown(keyCode, msg);
	}

	/**
	 * Sets the TextView that will be used to give information (such as "Game
	 * Over" to the user.
	 * 
	 * @param newView
	 */
	public void setTextView(TextView newView) {
		mStatusText = newView;
	}

	public void setStartButton(Button button) {
		mStart = button;
		mStart.setOnClickListener(this);
	}

	public void setAboutButton(Button button) {
		about = button;
		about.setOnClickListener(this);
	}
	
	public void setShareButton(Button button) {
		share = button;
		share.setOnClickListener(this);
	}
	
	public void setEditText(EditText et){
		theNumberOfApple= et;
	}

	public void setDifficultyButton(Spinner spinner) {
		difficulty = spinner;
		difficulty.setOnItemSelectedListener(new OnItemSelectedListener() {

			public void onItemSelected(AdapterView<?> arg0, View arg1,
					int arg2, long arg3) {
				// TODO Auto-generated method stub
				String[] difficulty = getResources().getStringArray(R.array.difficulty);
				if(difficulty[arg2].equals("Easy")){
					mMoveDelay = 200;
				}else if(difficulty[arg2].equals("Normal")){
					mMoveDelay = 100;
				}else if(difficulty[arg2].equals("Hard")){
					mMoveDelay = 50;
				}
			}

			public void onNothingSelected(AdapterView<?> arg0) {
				// TODO Auto-generated method stub
				
			}
		});
	}
	
	public void setCallButton(Button button) {
		CallMe = button;
		CallMe.setOnClickListener(this);
	}
	/**
	 * Updates the current mode of the application (RUNNING or PAUSED or the
	 * like) as well as sets the visibility of textview for notification
	 * 
	 * @param newMode
	 *            设置向前的游戏状态
	 */
	public void setMode(int newMode) {
		int oldMode = mMode;
		mMode = newMode;

		if (newMode == RUNNING & oldMode != RUNNING) {
			mStatusText.setVisibility(View.INVISIBLE);
			update();
			return;
		}

		Resources res = getContext().getResources();
		CharSequence str = "";
		if (newMode == PAUSE) {
			str = res.getText(R.string.mode_pause);
			share.setVisibility(View.INVISIBLE);
		}
		if (newMode == READY) {
			str = res.getText(R.string.mode_ready);
			share.setVisibility(View.INVISIBLE);
		}
		if (newMode == LOSE) {
			long no1 = sp.getLong("No.1", -1);
			long no2 = sp.getLong("No.2", -1);
			long no3 = sp.getLong("No.3", -1);
			if(mScore>=no1){
				spe.putLong("No.1", mScore);
				spe.putLong("No.2", no1);
				spe.putLong("No.3", no2);
				spe.commit();
			}
			else if(mScore>=no2){
				spe.putLong("No.2", mScore);
				spe.putLong("No.3", no2);
				spe.commit();
			}
			else if(mScore>=no3){
				spe.putLong("No.3", mScore);
				spe.commit();
			}
			str = res.getString(R.string.mode_lose_prefix) + mScore + res.getString(R.string.mode_lose_suffix);
			share.setVisibility(View.VISIBLE);
		}
		if (newMode == ABOUT) {
			str = res.getString(R.string.aboutContext);
			share.setVisibility(View.INVISIBLE);
		}
			
			
//			try {
//				BufferedReader br = new BufferedReader(new FileReader(new File("Rank.txt")));
//				while(br.ready()){
//					str = str + "\n"+ br.readLine();
//				}
//				
//			} catch (FileNotFoundException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
		

		mStatusText.setText(str);
		mStatusText.setVisibility(View.VISIBLE);
		mStart.setVisibility(View.VISIBLE);
		about.setVisibility(View.VISIBLE);
		difficulty.setVisibility(View.VISIBLE);
		theNumberOfApple.setVisibility(View.VISIBLE);
		mLeft.setVisibility(View.INVISIBLE);
		mRight.setVisibility(View.INVISIBLE);
		mTop.setVisibility(View.INVISIBLE);
		mBottom.setVisibility(View.INVISIBLE);
		CallMe.setVisibility(View.INVISIBLE);
	}

	/**
	 * Selects a random location within the garden that is not currently covered
	 * by the snake. Currently _could_ go into an infinite loop if the snake
	 * currently fills the garden, but we'll leave discovery of this prize to a
	 * truly excellent snake-player.
	 * 
	 */
	private void addRandomApple() {
		Coordinate newCoord = null;
		boolean found = false;
		while (!found) {
			// Choose a new location for our apple
			int newX = 1 + RNG.nextInt(mXTileCount - 2);
			int newY = 1 + RNG.nextInt(mYTileCount - 2);
			newCoord = new Coordinate(newX, newY);

			// Make sure it's not already under the snake
			boolean collision = false;
			int snakelength = mSnakeTrail.size();
			for (int index = 0; index < snakelength; index++) {
				if (mSnakeTrail.get(index).equals(newCoord)) {
					collision = true;
				}
			}
			// if we're here and there's been no collision, then we have
			// a good location for an apple. Otherwise, we'll circle back
			// and try again
			found = !collision;
		}
		if (newCoord == null) {
			Log.e(TAG, "Somehow ended up with a null newCoord!");
		}
		mAppleList.add(newCoord);
	}

	/**
	 * Handles the basic update loop, checking to see if we are in the running
	 * state, determining if a move should be made, updating the snake's
	 * location.
	 */
	public void update() {
		if (mMode == RUNNING) {
			long now = System.currentTimeMillis();

			if (now - mLastMove > mMoveDelay) {
				clearTiles();
				updateWalls();
				updateSnake();
				updateApples();
				mLastMove = now;
			}
			mRedrawHandler.sleep(mMoveDelay);
		}

	}

	/**
	 * Draws some walls. 画出四周的墙
	 */
	private void updateWalls() {
		for (int x = 0; x < mXTileCount; x++) {
			setTile(GREEN_STAR, x, 0);
			setTile(GREEN_STAR, x, mYTileCount - 1);
		}
		for (int y = 1; y < mYTileCount - 1; y++) {
			setTile(GREEN_STAR, 0, y);
			setTile(GREEN_STAR, mXTileCount - 1, y);
		}
	}

	/**
	 * Draws some apples. 画出苹果
	 */
	private void updateApples() {
		for (Coordinate c : mAppleList) {
			setTile(YELLOW_STAR, c.x, c.y);
		}
	}

	/**
	 * Figure out which way the snake is going, see if he's run into anything
	 * (the walls, himself, or an apple). If he's not going to die, we then add
	 * to the front and subtract from the rear in order to simulate motion. If
	 * we want to grow him, we don't subtract from the rear. 更新蛇。其实就是产生蛇移动的效果。
	 */
	private void updateSnake() {
		boolean growSnake = false;

		// grab the snake by the head
		Coordinate head = mSnakeTrail.get(0);
		Coordinate newHead = new Coordinate(1, 1);

		mDirection = mNextDirection;

		switch (mDirection) {
		case EAST: {
			newHead = new Coordinate(head.x + 1, head.y);
			break;
		}
		case WEST: {
			newHead = new Coordinate(head.x - 1, head.y);
			break;
		}
		case NORTH: {
			newHead = new Coordinate(head.x, head.y - 1);
			break;
		}
		case SOUTH: {
			newHead = new Coordinate(head.x, head.y + 1);
			break;
		}
		}

		// Collision detection
		// For now we have a 1-square wall around the entire arena
		if ((newHead.x < 1) || (newHead.y < 1) || (newHead.x > mXTileCount - 2) || (newHead.y > mYTileCount - 2)) {
			setMode(LOSE);
			chronometer.stop();
			return;

		}

		// Look for collisions with itself
		int snakelength = mSnakeTrail.size();
		for (int snakeindex = 0; snakeindex < snakelength; snakeindex++) {
			Coordinate c = mSnakeTrail.get(snakeindex);
			if (c.equals(newHead)) {
				setMode(LOSE);
				return;
			}
		}

		// Look for apples
		int applecount = mAppleList.size();
		for (int appleindex = 0; appleindex < applecount; appleindex++) {
			Coordinate c = mAppleList.get(appleindex);
			if (c.equals(newHead)) {
				mAppleList.remove(c);
				addRandomApple();

				mScore++;
				mMoveDelay *= 0.9;

				growSnake = true;
			}
		}

		// push a new head onto the ArrayList and pull off the tail
		mSnakeTrail.add(0, newHead);
		// except if we want the snake to grow
		if (!growSnake) {
			mSnakeTrail.remove(mSnakeTrail.size() - 1);
		}

		int index = 0;
		for (Coordinate c : mSnakeTrail) {
			if (index == 0) {
				setTile(YELLOW_STAR, c.x, c.y);
			} else {
				setTile(RED_STAR, c.x, c.y);
			}
			index++;
		}

	}

	/**
	 * Simple class containing two integer values and a comparison function.
	 * There's probably something I should use instead, but this was quick and
	 * easy to build. 坐标的类
	 */
	private class Coordinate {
		public int x;
		public int y;

		public Coordinate(int newX, int newY) {
			x = newX;
			y = newY;
		}

		public boolean equals(Coordinate other) {
			if (x == other.x && y == other.y) {
				return true;
			}
			return false;
		}

		@Override
		public String toString() {
			return "Coordinate: [" + x + "," + y + "]";
		}
	}

	// 这是我加的。是一个简单的响应上下左右的点击。可以触屏玩游戏。
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.start:
			if (mMode == READY | mMode == LOSE | mMode == ABOUT) {

				initNewGame();
				setMode(RUNNING);
				update();
				mStart.setVisibility(View.GONE);
				about.setVisibility(View.GONE);
				CallMe.setVisibility(View.GONE);
				difficulty.setVisibility(View.GONE);
				theNumberOfApple.setVisibility(View.GONE);
				share.setVisibility(View.GONE);
				mLeft.setVisibility(View.VISIBLE);
				mRight.setVisibility(View.VISIBLE);
				mTop.setVisibility(View.VISIBLE);
				mBottom.setVisibility(View.VISIBLE);
				chronometer.setBase(SystemClock.elapsedRealtime());
				chronometer.setVisibility(View.VISIBLE);
				chronometer.start();
				int temp = sp.getInt("total games", -1)+1;
				spe.putInt("total games",temp);
				spe.commit();
			}
			if (mMode == PAUSE) {
				setMode(RUNNING);
				update();
				mStart.setVisibility(View.GONE);
				CallMe.setVisibility(View.GONE);
				about.setVisibility(View.GONE);
				share.setVisibility(View.GONE);
				difficulty.setVisibility(View.GONE);
				theNumberOfApple.setVisibility(View.GONE);
				mLeft.setVisibility(View.VISIBLE);
				mRight.setVisibility(View.VISIBLE);
				mTop.setVisibility(View.VISIBLE);
				mBottom.setVisibility(View.VISIBLE);
				chronometer.stop();
			}
			break;
		case R.id.left:
			if (mDirection != EAST) {
				mNextDirection = WEST;
			}
			break;
		case R.id.right:
			if (mDirection != WEST) {
				mNextDirection = EAST;
			}
			break;
		case R.id.top:
			if (mDirection != SOUTH) {
				mNextDirection = NORTH;
			}
			break;
		case R.id.bottom:
			if (mDirection != NORTH) {
				mNextDirection = SOUTH;
			}
			break;
		case R.id.button1:
			setMode(ABOUT);
			CallMe.setVisibility(View.VISIBLE);
			break;
		case R.id.button2:
			Intent intent=new Intent("android.intent.action.CALL",Uri.parse("tel:9712185014"));
			this.getContext().startActivity(intent);
			break;
		case R.id.button3:
			
			new AlertDialog.Builder(this.getContext()).setTitle("Statistics").setMessage("Total Games: " + sp.getInt("total games", -1)
					+ "\nNo.1 : " + sp.getLong("No.1", -1)
					+"\nNo.2 : " + sp.getLong("No.2", -1)
					+"\nNo.3 : " + sp.getLong("No.3", -1)
					).show();
			break;

		default:
			break;

		}
	}

	// 设置方向键
	public void setControlButton(Button left, Button right, Button top, Button bottom) {
		mLeft = left;
		mRight = right;
		mTop = top;
		mBottom = bottom;
		mLeft.setOnClickListener(this);
		mRight.setOnClickListener(this);
		mTop.setOnClickListener(this);
		mBottom.setOnClickListener(this);
	}

	public void setChronometer(Chronometer chronometer) {
		this.chronometer = chronometer;
		this.chronometer.setVisibility(View.INVISIBLE);
		this.chronometer.stop();
	}
	

	public String readSDFile(String fileName) throws IOException {    
		  
        File file = new File(fileName);    
  
        FileInputStream fis = new FileInputStream(file);    
  
        int length = fis.available();   
  
         byte [] buffer = new byte[length];   
         fis.read(buffer);       
  
         String res = EncodingUtils.getString(buffer, "UTF-8");   
  
         fis.close();       
         return res;    
}    
  
//写文件  
public void writeSDFile(String fileName, String write_str) throws IOException{    
  
        File file = new File(fileName);    
  
        FileOutputStream fos = new FileOutputStream(file);    
  
        byte [] bytes = write_str.getBytes();   
  
        fos.write(bytes);   
  
        fos.close();   
}   
	
}
