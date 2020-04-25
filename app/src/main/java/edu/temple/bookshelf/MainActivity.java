package edu.temple.bookshelf;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import edu.temple.audiobookplayer.AudiobookService;

import static java.lang.Thread.sleep;

public class MainActivity extends AppCompatActivity implements BookListFragment.BookSelectedInterface,BookDetailsFragment.BookPlayInterface {

    FragmentManager fm;
    BookDetailsFragment bookDetailsFragment;
    SeekBar seekBar = null;
    TextView playingTextView = null;
    boolean onePane;
    Library library;
    BookStatus bookStatus;
    Fragment current1, current2;
    AudiobookService.MediaControlBinder binderService;
    boolean connected;
    boolean localFile;
    boolean playing;
    boolean isTurning;
    int curBookId;
    int playBookId;

    Handler seekbarHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if(msg == null)
                return;
            if(msg.obj == null) {
                return;
            }
            int pos = ((AudiobookService.BookProgress)msg.obj).getProgress();
            if(seekBar != null)
                seekBar.setProgress(pos);
        }
    };

    ServiceConnection myConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            connected = true;
            binderService = (AudiobookService.MediaControlBinder) service;
            binderService.setProgressHandler(seekbarHandler);

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            connected = false;
            binderService = null;
        }
    };

    private final String SEARCH_URL = "https://kamorris.com/lab/audlib/booksearch.php?search=";
    private final String DOWNLOAD_URL = "https://kamorris.com/lab/audlib/download.php?id=";
    private final String BOOK_LIST_FILE = "myBookList";
    private final String BOOK_STAT_FILE = "myBookStat";

    Handler bookHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) {

            try {
                library.clear();
                JSONArray booksArray = new JSONArray((String) message.obj);
                for (int i = 0; i < booksArray.length(); i++) {
                    library.addBook(new Book(booksArray.getJSONObject(i)));
                }

                if(fm.findFragmentById(R.id.container_1) == null)
                    setUpDisplay();
                else
                    updateBooks();

                //saving book list to file
                Context context = getApplicationContext();
                FileOutputStream fos = context.openFileOutput(BOOK_LIST_FILE, Context.MODE_PRIVATE);
                ObjectOutputStream os = new ObjectOutputStream(fos);
                os.writeObject(library);
                os.close();
                fos.close();
                System.out.printf("====== saved book list to file.\n");
            } catch (JSONException | IOException e) {
                e.printStackTrace();
            }
            return true;
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        playingTextView = findViewById(R.id.playingText);
        isTurning = false;
        localFile = false;
        playing = false;

        Intent serviceIntent = new Intent(this, AudiobookService.class);
        bindService(serviceIntent, myConnection, Context.BIND_AUTO_CREATE);

        curBookId = 0;
        playBookId = 0;
        fm = getSupportFragmentManager();
        library = new Library();
        readBookStatus();

        // Check for fragments in both containers
        current1 = fm.findFragmentById(R.id.container_1);
        current2 = fm.findFragmentById(R.id.container_2);

        onePane = findViewById(R.id.container_2) == null;

        if (current1 == null) {
            //fetchBooks(null);
            // loading book list
            Context context = getApplicationContext();
            try {
                FileInputStream fis = context.openFileInput(BOOK_LIST_FILE);
                ObjectInputStream is = new ObjectInputStream(fis);
                library = (Library)is.readObject();
                is.close();
                fis.close();
                System.out.printf("====== Loaded local book list.\n");
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
                System.out.printf("====== fetch book list.\n");
                fetchBooks(null);
            }

            //if(fm.findFragmentById(R.id.container_1) == null)
            setUpDisplay();
            //else
            //    updateBooks();
        } else {
            updateDisplay();
        }

        setEventOfControls();
    }

    private void setEventOfControls(){
        findViewById(R.id.searchButton).setOnClickListener(v -> fetchBooks(((EditText) findViewById(R.id.searchBox)).getText().toString()));

        findViewById(R.id.downloadButton).setOnClickListener(v -> {
            BookAudio_download();
        });

        findViewById(R.id.deleteButton).setOnClickListener(v -> {
            BookAudio_delete();
        });

        findViewById(R.id.pauseButton).setOnClickListener(v -> {
            BookPlay_pause();
        });

        findViewById(R.id.stopButton).setOnClickListener(v -> {
            BookPlay_stop();
        });

        seekBar = (SeekBar)findViewById(R.id.seekBar);
        seekBar.setOnSeekBarChangeListener(new SeekBar
                .OnSeekBarChangeListener() {
            int pval = 0;

            // When the progress value has changed
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
            {
                pval = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar)
            {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar)
            {
                binderService.seekTo(pval);
                int duration = seekBar.getMax();
                String tips = String.format("Progress: %.2f%%",((float) pval / (float)duration) * 100);
                Toast.makeText(getApplicationContext(), tips,Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.pauseButton).setOnLongClickListener(v -> {
            Toast.makeText(v.getContext(), "Pause", Toast.LENGTH_SHORT).show();
            return true;
        });
        findViewById(R.id.stopButton).setOnLongClickListener(v -> {
            Toast.makeText(v.getContext(), "Stop", Toast.LENGTH_SHORT).show();
            return true;

        });
    }
    @Override
    protected void onStart() {
        super.onStart();
        System.out.printf("===== activity onStart\n");
    }
    @Override
    protected void onResume() {
        super.onResume();
        System.out.printf("===== activity onResume\n");
    }
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);

        System.out.printf("===== activity onSaveInstanceState\n");
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        isTurning = true;
        Fragment tmpFragment = current1;
        if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {

            setContentView(R.layout.activity_main);
            onePane = true;
            if (current1 instanceof BookListFragment) {
                current1 = ViewPagerFragment.newInstance(library);

                fm.beginTransaction()
                        .remove(tmpFragment)
                        .add(R.id.container_1, current1)
                        .commit();
                new Thread(){
                    @Override
                    public void run() {

                        ViewPager viewPager = ((ViewPagerFragment)current1).getViewPager();
                        //System.out.printf("====== turning: %d\n",1);
                        while (viewPager == null) {
                            try {
                                sleep(100);
                                viewPager = ((ViewPagerFragment)current1).getViewPager();

                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }

                        setViewPagerEvent(viewPager);

                        viewPager.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                ViewPager viewPager = ((ViewPagerFragment)current1).getViewPager();
                                int i = library.getPositionById(curBookId);
                                viewPager.setCurrentItem(i);
                            }
                        }, 100);
                    }
                }.start();
                setEventOfControls();
                playingTextView = findViewById(R.id.playingText);
                if(connected){
                    if(binderService.isPlaying())
                        showNowPlaying();
                }
            }
        } else if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setContentView(R.layout.two_pane_base);
            onePane = false;

            if (current1 instanceof ViewPagerFragment) {
                ViewPager viewPager = ((ViewPagerFragment)current1).getViewPager();
                viewPager.removeAllViews();
                fm.beginTransaction().remove(tmpFragment).commit();

                current1 = BookListFragment.newInstance(library);

                Book book1 = library.getBookById(curBookId);
                if (book1 == null) {
                    System.out.printf("====== LANDSCAPE get book error !!\n");
                    return;
                }
                bookDetailsFragment = BookDetailsFragment.newInstance(book1);
                fm.beginTransaction()
                        .remove(tmpFragment)
                        .add(R.id.container_1, current1)
                        .add(R.id.container_2, bookDetailsFragment)
                        .commit();

                setEventOfControls();
                playingTextView = findViewById(R.id.playingText);
                if(connected){

                    if(binderService.isPlaying())
                        showNowPlaying();
                }
            }

        }
    }

    //switching on one page
    private void setViewPagerEvent(ViewPager viewPager){
        //ViewPager viewPager = ((ViewPagerFragment)current1).getViewPager();
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int i, float v, int i1) {
                //System.out.printf("====== onPageScrolled: %d\n",i);
            }

            @Override
            public void onPageSelected(int position) {
                curBookId = library.getBookAt(position).getId();
                //curBookId = library.getBookById(position).getId();
                //curBookId = position + 1;
            }

            @Override
            public void onPageScrollStateChanged(int i) {
                if(i == 0)
                    isTurning = false;
            }
        });
    }

    private void setUpDisplay() {
        // If there are no fragments at all (first time starting activity)

        if (onePane) {
            curBookId = 1;
            current1 = ViewPagerFragment.newInstance(library);
            fm.beginTransaction()
                    .add(R.id.container_1, current1)
                    .commit();

            new Thread(){
                @Override
                public void run() {

                    ViewPager viewPager = ((ViewPagerFragment) current1).getViewPager();
                    //System.out.printf("====== turning: %d\n",1);
                    while (viewPager == null) {
                        try {
                            sleep(100);
                            viewPager = ((ViewPagerFragment) current1).getViewPager();

                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    setViewPagerEvent(viewPager);
                }
            }.start();
        } else {
            current1 = BookListFragment.newInstance(library);
            bookDetailsFragment = new BookDetailsFragment();
            fm.beginTransaction()
                    .add(R.id.container_1, current1)
                    .add(R.id.container_2, bookDetailsFragment)
                    .commit();
        }

    }

    private void updateDisplay () {
        Fragment tmpFragment = current1;;
        library = ((Displayable) current1).getBooks();
        if (onePane) {

            if (current1 instanceof BookListFragment) {
                current1 = ViewPagerFragment.newInstance(library);
                // If we have the wrong fragment for this configuration, remove it and add the correct one
                fm.beginTransaction()
                        .remove(tmpFragment)
                        .add(R.id.container_1, current1)
                        .commit();
            }
        } else {
            if (current1 instanceof ViewPagerFragment) {
                current1 = BookListFragment.newInstance(library);
                fm.beginTransaction()
                        .remove(tmpFragment)
                        .add(R.id.container_1, current1)
                        .commit();
            }
            if (current2 instanceof BookDetailsFragment)
                bookDetailsFragment = (BookDetailsFragment) current2;
            else {
                bookDetailsFragment = new BookDetailsFragment();
                fm
                        .beginTransaction()
                        .add(R.id.container_2, bookDetailsFragment)
                        .commit();
            }
        }

        bookDetailsFragment = (BookDetailsFragment) current2;
    }

    private void updateBooks() {
        ((Displayable) current1).setBooks(library);
    }

    private void readBookStatus(){

        Context context = getApplicationContext();
        try {
            FileInputStream fis = context.openFileInput(BOOK_STAT_FILE);
            ObjectInputStream is = new ObjectInputStream(fis);
            bookStatus = (BookStatus) is.readObject();
            is.close();
            fis.close();
            System.out.printf("====== Loaded local book status from file.\n");
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            System.out.printf("====== new book status file.\n");
            bookStatus = new BookStatus();
            for(int i=0;i<8;i++){
                bookStatus.setStatus(i,0);
            }
        }
    }

    //saving book status to file
    private void saveBookStatus(){

        Context context = getApplicationContext();
        try {
            FileOutputStream fos = context.openFileOutput(BOOK_STAT_FILE, Context.MODE_PRIVATE);
            ObjectOutputStream os = new ObjectOutputStream(fos);
            os.writeObject(bookStatus);
            os.close();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
            System.out.printf("====== save book status file faiil.\n");
        }
    }

    private void BookPlay_stop(){
        if(!playing)
            return;
        playing = false;
        //if(connected)
        binderService.stop();
        seekBar.setProgress(0);
        playingTextView.setText("");

        if(localFile){
            localFile = false;
            bookStatus.setStatus(playBookId, 0);
            saveBookStatus();
        }
    }

    private void BookPlay_pause(){
        binderService.pause();
        if(localFile){
            int pos = seekBar.getProgress();
            bookStatus.setStatus(playBookId, pos);
            System.out.printf("===== BookPlay_pause save play position:%d\n",pos);
            saveBookStatus();
        }

    }
    private void BookAudio_download(){
        Context context = getApplicationContext();
        String myFilePath = context.getFilesDir() + "/" + String.valueOf(curBookId);
        File file = new File(myFilePath);
        if(file.canRead()){
            Toast.makeText(context, "File has existed.", Toast.LENGTH_SHORT).show();
            System.out.printf("===== File has existed.\n");
            return;
        }
        String downloadUrl = DOWNLOAD_URL + String.valueOf(curBookId);
        //System.out.printf("===== %s\n", downloadUrl);
        new Thread() {
            @Override
            public void run() {
                try {
                    URL url = new URL(downloadUrl);
                    HttpURLConnection c = (HttpURLConnection) url.openConnection();
                    c.setRequestMethod("GET");
                    c.connect();
                    String downloadFileName = String.valueOf(curBookId);
                    //System.out.printf("===== download audio file name:%s\n", downloadFileName);
                    //
                    Context context = getApplicationContext();
                    FileOutputStream fos = context.openFileOutput(downloadFileName, Context.MODE_PRIVATE);
                    InputStream is = c.getInputStream();
                    byte[] buffer = new byte[1024];
                    int len1 = 0;
                    while ((len1 = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len1);
                    }
                    is.close();
                    fos.close();

                    String tips =  String.format("Audio file:%s Download completed.\n", downloadFileName);
                    //System.out.printf("====== %s\n",tips);

                    runOnUiThread(new Runnable() {
                        public void run() {
                            final Toast toast = Toast.makeText(context, tips, Toast.LENGTH_SHORT);
                            toast.show();
                        }
                    });

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    private void BookAudio_delete(){
        Context context = getApplicationContext();
        String myFilePath = context.getFilesDir() + "/" + String.valueOf(curBookId);
        File file = new File(myFilePath);
        boolean deleted = file.delete();
        if(deleted) {
            String tips = String.format("Deleted file:%d\n",curBookId);
            Toast.makeText(getApplicationContext(), tips, Toast.LENGTH_SHORT).show();
            System.out.printf("===== %s\n",tips);
            bookStatus.setStatus(curBookId, 0);
        }
        else {
            Toast.makeText(getApplicationContext(), "File doesn't exist", Toast.LENGTH_SHORT).show();
            System.out.printf("===== delete file:%d fail.\n", curBookId);
        }
    }

    //=====================interface about book==============================
    @Override
    public void bookSelected(Book book) {

        if (bookDetailsFragment == null)
            return;
        //if(connected)
        //    binderService.stop();
        curBookId = book.getId();
        bookDetailsFragment.changeBook(book);
        //setSeekBarRange(book.getDuration());
    }

    @Override
    public void bookPlay(int bookId) {

        Context context = getApplicationContext();

        if(localFile){
            int pos = seekBar.getProgress();
            bookStatus.setStatus(playBookId, pos);
            Book book = library.getBookById(playBookId);
            System.out.printf("===== bookPlay save pri_play position:%d\n", pos);
            saveBookStatus();
        }

        String myFilePath = context.getFilesDir() + "/" + String.valueOf(bookId);
        File file = new File(myFilePath);
        /*
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            int len1 = 0; int total = 0;
            byte[] buffer = new byte[1024];
            while ((len1 = fis.read(buffer,0, 1024)) != -1) {
                //fos.write(buffer, 0, len1);
                total += len1;
            }
            System.out.printf("====== read file %s, size:%d\n",myFilePath, total);
        } catch (IOException e) {
            e.printStackTrace();
        }*/
        if(file.canRead()){
            Book book = library.getBookById(bookId);
            if (book == null) {
                System.out.printf("====== bookPlay get book error !!\n");
                return;
            }
            localFile = true;
            int position = bookStatus.getStatus(bookId) - 10;
            if(position < 0)
                position = 0;
            binderService.play(file, position);
            playing = true;
            String tips =  String.format("Play local file: %d.\n", bookId);
            Toast.makeText(getApplicationContext(), tips,Toast.LENGTH_SHORT).show();
            System.out.printf("======= %s\n",tips);
        }
        else if (connected){
            localFile = false;
            playing = true;
            binderService.play(bookId);
            String tips =  String.format("Play stream:%d.\n", bookId);
            Toast.makeText(getApplicationContext(), tips,Toast.LENGTH_SHORT).show();
            System.out.printf("====== %s\n",tips);
        }
        else
            return;

        curBookId = bookId;
        playBookId = bookId;
        showNowPlaying();
    }

    //@Override
    private void setSeekBarRange(int n)
    {
        seekBar.setMax(n);
        seekBar.setProgress(0);
    }

    @Override
    public void setCurrentDetailFrag(BookDetailsFragment bdf)
    {
        bookDetailsFragment = bdf;
    }

    private void showNowPlaying() {
        System.out.printf("====== showNowPlaying bookId:%d\n", playBookId);
        Book book = library.getBookById(playBookId);
        if (book == null) {
            System.out.printf("====== showNowPlaying get book error !!\n");
            return;
        }
        setSeekBarRange(book.getDuration());
        playingTextView.setText("Now playing: "+ book.getTitle());
    }

    //===================================================

    private boolean isNetworkActive() {
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    private void fetchBooks(final String searchString) {
        new Thread() {
            @Override
            public void run() {
                if (isNetworkActive()) {

                    URL url;

                    try {
                        url = new URL(SEARCH_URL + (searchString != null ? searchString : ""));
                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(url.openStream()));

                        StringBuilder response = new StringBuilder();
                        String tmpResponse;

                        while ((tmpResponse = reader.readLine()) != null) {
                            response.append(tmpResponse);
                        }

                        Message msg = Message.obtain();

                        msg.obj = response.toString();

                        Log.d("Books RECEIVED", response.toString());

                        bookHandler.sendMessage(msg);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                } else {
                    Log.e("Network Error", "Cannot download books");
                }
            }
        }.start();
    }

}
