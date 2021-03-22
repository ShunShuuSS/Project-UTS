package umn.ac.id.projectuts;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ListSongActivity extends AppCompatActivity implements
        Recycle.RecycleSongTouchHelperListener, SongAdapter.SongAdapterListener,
        View.OnClickListener, MediaPlayer.OnCompletionListener,
        AudioManager.OnAudioFocusChangeListener, SeekBar.OnSeekBarChangeListener {
    private static final Uri MEDIA_URI = Uri.parse("content://media");
    private static Uri sArtworkUri = Uri.parse("content://media/external/audio/albumart");
    private final int STORAGE_PERMISSION_ID = 0;
    private List<Song> mSongList = new ArrayList<>();
    private RecyclerView mRecyclerViewSongs;
    private SongAdapter mAdapter;
    private CoordinatorLayout mCoordinatorLayout;
    private LinearLayout mMediaLayout;
    private MediaPlayer mMediaPlayer;

    // TODO list
    private TextView mTvTitle;
    private ImageView mIvArtwork;
    private ImageView mIvPlay;
    private ImageView mIvPrevious;
    private ImageView mIvNext;
    private boolean isPlaying = false;
    private SeekBar songProgressBar;
    private TextView mTvCurrentDuration;
    private TextView mTvTotalDuration;
    private Time timeUtil;
    private int currentSongIndex;
    private Handler mHandler = new Handler();
    private Runnable mUpdateTimeTask = new Runnable() {
        public void run() {
            if (mMediaPlayer == null) return;
            long totalDuration = mMediaPlayer.getDuration();
            long currentDuration = mMediaPlayer.getCurrentPosition();
            mTvTotalDuration
                    .setText(String.format("%s", timeUtil.milliSecondsToTimer(totalDuration)));
            mTvCurrentDuration
                    .setText(String.format("%s", timeUtil.milliSecondsToTimer(currentDuration)));
            int progress = (timeUtil.getProgressPercentage(currentDuration, totalDuration));
            songProgressBar.setProgress(progress);
            mHandler.postDelayed(this, 100);
        }
    };

    private AlertDialog.Builder dialog;
    private LayoutInflater inflater;
    private View dialogView;
    private boolean popUpShown = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.list_song);
        init();
        setUpAdapter();
        setUpListeners();
        getSongList();
        if(popUpShown == false){
            welcomeMessage();
            popUpShown = true;
        }
        playsong();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.optionmenu_list_song, menu);
//        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;

    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId()==R.id.about){
            startActivity(new Intent(this, ProfileLogActivity.class));
        } else if (item.getItemId() == R.id.logout) {
            startActivity(new Intent(this, MainActivity.class));
        }else {
            Log.d(this.getClass().getName(), "Option selected");
            super.onBackPressed();
        }
        return true;
    }

    private void init(){
        if (!checkStorePermission(STORAGE_PERMISSION_ID)) {
            showRequestPermission(STORAGE_PERMISSION_ID);
        }
        mMediaPlayer = new MediaPlayer();
        timeUtil = new Time();
        mRecyclerViewSongs = findViewById(R.id.recycle_list_song);
        mCoordinatorLayout = findViewById(R.id.coordinator_layout);
        mMediaLayout = findViewById(R.id.layout_media);
        // TODO
        mIvArtwork = findViewById(R.id.iv_artwork);
        mIvPlay = findViewById(R.id.iv_play);
        mIvPrevious = findViewById(R.id.iv_previous);
        mIvNext = findViewById(R.id.iv_next);
        mTvTitle = findViewById(R.id.tv_title);
        mTvCurrentDuration = findViewById(R.id.songCurrentDurationLabel);
        mTvTotalDuration = findViewById(R.id.songTotalDurationLabel);
        songProgressBar = findViewById(R.id.songProgressBar);
    }

    private void setUpAdapter(){
        mAdapter = new SongAdapter(getApplicationContext(), mSongList, this);
        RecyclerView.LayoutManager mLayoutManager =
                new LinearLayoutManager(getApplicationContext());
        mRecyclerViewSongs.setLayoutManager(mLayoutManager);
        mRecyclerViewSongs.setItemAnimator(new DefaultItemAnimator());
        mRecyclerViewSongs.setAdapter(mAdapter);
    }

    private void setUpListeners(){
        ItemTouchHelper.SimpleCallback itemTouchHelperCallback =
                new Recycle(0, ItemTouchHelper.LEFT, this);
        new ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(mRecyclerViewSongs);
        mIvPlay.setOnClickListener(this);
        mIvPrevious.setOnClickListener(this);
        mIvNext.setOnClickListener(this);
        songProgressBar.setOnSeekBarChangeListener(this);
        mMediaPlayer.setOnCompletionListener(this);
    }

    private void getSongList(){
        //retrieve item_song info
        ContentResolver musicResolver = getContentResolver();
        Uri musicUri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Cursor musicCursor = musicResolver.query(musicUri, null, null, null, null);
        if (musicCursor != null && musicCursor.moveToFirst()) {
            //get columns
            int titleColumn = musicCursor.getColumnIndex
                    (android.provider.MediaStore.Audio.Media.TITLE);
            int idColumn = musicCursor.getColumnIndex
                    (android.provider.MediaStore.Audio.Media._ID);
            int albumID = musicCursor.getColumnIndex
                    (MediaStore.Audio.Media.ALBUM_ID);
            int artistColumn = musicCursor.getColumnIndex
                    (android.provider.MediaStore.Audio.Media.ARTIST);
            int songLink = musicCursor.getColumnIndex
                    (MediaStore.Audio.Media.DATA);
            //add songs to list
            do {
                long thisId = musicCursor.getLong(idColumn);
                String thisTitle = musicCursor.getString(titleColumn);
                String thisArtist = musicCursor.getString(artistColumn);
                Uri thisSongLink = Uri.parse(musicCursor.getString(songLink));
                long some = musicCursor.getLong(albumID);
                Uri uri = ContentUris.withAppendedId(sArtworkUri, some);
                mSongList.add(new Song(thisId, thisTitle, thisArtist, uri.toString(),
                        thisSongLink.toString()));
            }
            while (musicCursor.moveToNext());
        }
        assert musicCursor != null;
        musicCursor.close();
        // Sort music alphabetically
        Collections.sort(mSongList, new Comparator<Song>() {
            public int compare(Song a, Song b) {
                return a.getTitle().compareTo(b.getTitle());
            }
        });
        mAdapter.notifyDataSetChanged();
    }

    private void welcomeMessage(){
        // TODO pop up menu
        dialog = new AlertDialog.Builder(ListSongActivity.this);
        inflater = getLayoutInflater();
        dialogView = inflater.inflate(R.layout.pop_up, null);
        dialog.setView(dialogView);
        dialog.setCancelable(true);
        dialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    private boolean checkStorePermission(int permission) {
        if (permission == STORAGE_PERMISSION_ID) {
            return Permission.checkPermissions(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE);
        } else {
            return true;
        }
    }

    private void showRequestPermission(int requestCode) {
        String[] permissions;
        if (requestCode == STORAGE_PERMISSION_ID) {
            permissions = new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
        }
        Permission.requestPermissions(this, requestCode, permissions);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 0) {
            for (int i = 0, len = permissions.length; i < len; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    getSongList();
                    return;
                }
            }
        }
    }

    @Override
    public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction, int position) {
        if (viewHolder instanceof SongAdapter.MyViewHolder) {
            String name = mSongList.get(viewHolder.getAdapterPosition()).getTitle();
            final Song deletedItem = mSongList.get(viewHolder.getAdapterPosition());
            final int deletedIndex = viewHolder.getAdapterPosition();
            mAdapter.removeItem(viewHolder.getAdapterPosition());
            Snackbar snackbar = Snackbar
                    .make(mCoordinatorLayout, name + " removed from library!", Snackbar.LENGTH_LONG);
            snackbar.setAction("UNDO", new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mAdapter.restoreItem(deletedItem, deletedIndex);
                }
            });
            snackbar.setActionTextColor(Color.YELLOW);
            snackbar.show();
        }
    }

    @Override
    public void onSongSelected(Song song) {
        playSong(song);
        currentSongIndex = mSongList.indexOf(song);
    }

    public boolean deleteMusic(final File file) {
        final String where = MediaStore.MediaColumns.DATA + "=?";
        final String[] selectionArgs = new String[]{
                file.getAbsolutePath()
        };
        final ContentResolver contentResolver = ListSongActivity.this.getContentResolver();
        final Uri filesUri = MediaStore.Files.getContentUri("external");
        contentResolver.delete(filesUri, where, selectionArgs);
        if (file.exists()) {
            contentResolver.delete(filesUri, where, selectionArgs);
        }
        return !file.exists();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.iv_play:
                playMusic();
                break;
            case R.id.iv_previous:
                playPreviousSong();
                break;
            case R.id.iv_next:
                playNextSong();
                break;
            default:
                break;
        }
    }

    private void playMusic() {
        if (isPlaying) {
            mIvPlay.setBackground(getResources().getDrawable(android.R.drawable.ic_media_pause));
            isPlaying = false;
            mMediaPlayer.start();
            return;
        }
        mIvPlay.setBackground(getResources().getDrawable(android.R.drawable.ic_media_play));
        mMediaPlayer.pause();
        isPlaying = true;
    }

    public void playSong(Song song) {
        try {
            mMediaPlayer.reset();
            mMediaPlayer.setDataSource(song.getSongLink());
            Log.d(null, song.getSongLink());
            mMediaPlayer.prepare();
            mMediaPlayer.start();
            isPlaying = true;
            mIvPlay.setBackground(getResources().getDrawable(android.R.drawable.ic_media_pause));
            mMediaLayout.setVisibility(View.VISIBLE);
            mTvTitle.setText(song.getTitle());
            Glide.with(this).load(song.getThumbnail()).placeholder(R.mipmap
                    .music_pic1).error(R.mipmap.music_pic1)
                    .crossFade().centerCrop().into(mIvArtwork);
            songProgressBar.setProgress(0);
            songProgressBar.setMax(100);
            updateProgressBar();
        } catch (IllegalArgumentException | IllegalStateException | IOException | SecurityException e) {
            e.printStackTrace();
        }
    }

    private void playNextSong() {
        if (currentSongIndex < (mSongList.size() - 1)) {
            Song song = mSongList.get(currentSongIndex + 1);
            playSong(song);
            currentSongIndex = currentSongIndex + 1;
        } else {
            playSong(mSongList.get(0));
            currentSongIndex = 0;
        }
    }
    private void playPreviousSong() {
        if (currentSongIndex > 0) {
            Song song = mSongList.get(currentSongIndex - 1);
            playSong(song);
            currentSongIndex = currentSongIndex - 1;
        } else {
            Song song = mSongList.get(mSongList.size() - 1);
            playSong(song);
            currentSongIndex = mSongList.size() - 1;
        }
    }

    public void updateProgressBar() {
        mHandler.postDelayed(mUpdateTimeTask, 100);
    }
    @Override
    public void onAudioFocusChange(int focusChange) {
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (currentSongIndex < (mSongList.size() - 1)) {
            playSong(mSongList.get(currentSongIndex + 1));
            currentSongIndex = currentSongIndex + 1;
        } else {
            playSong(mSongList.get(0));
            currentSongIndex = 0;
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        mHandler.removeCallbacks(mUpdateTimeTask);
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        mHandler.removeCallbacks(mUpdateTimeTask);
        int totalDuration = mMediaPlayer.getDuration();
        int currentPosition = timeUtil.progressToTimer(seekBar.getProgress(), totalDuration);
        mMediaPlayer.seekTo(currentPosition);
        updateProgressBar();
    }

    private void playsong(){
        mMediaLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent loginIntent = new Intent(ListSongActivity.this, PlaySong.class);
                startActivity(loginIntent);
            }
        });

    }
}
