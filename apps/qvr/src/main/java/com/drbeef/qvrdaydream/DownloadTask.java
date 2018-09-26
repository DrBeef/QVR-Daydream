package com.drbeef.qvrdaydream;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;


class DownloadTask extends AsyncTask<Void, String, Void> {
	
	private Context context;
	
	public boolean isDownloading = false;

	public String progress;

	private String url = "https://www.dropbox.com/s/cgd8ynsvp8aen5v/quake106.zip?dl=1";
	private String demofile = QVRConfig.GetFullWorkingFolder() + "quake106.zip";
	private String pakfile = QVRConfig.GetFullWorkingFolder() + "id1/pak0.pak";

	public DownloadTask set_context(Context context){
		this.context = context;
		return this;
	}
	
	@Override
	protected void  onPreExecute  (){

		isDownloading = true;
	 
	}
	
	
	public static String printSize( int size ){
		
		if ( size >= (1<<20) )
			return String.format("%.1f MB", size * (1.0/(1<<20)));
		
		if ( size >= (1<<10) )
			return String.format("%.1f KB", size * (1.0/(1<<10)));
		
		return String.format("%d bytes", size);
		
	}

	
	private void download_demo() throws Exception{
		
		Log.i( "DownloadTask.java", "starting to download "+ url);
		
	    if (new File(demofile).exists()){
	    	Log.i( "DownloadTask.java", demofile + " already there. skipping.");
	    	return;
	    }

		/// setup output directory		
		new File(QVRConfig.GetFullWorkingFolder() + "id1").mkdirs();
		
       	InputStream     is = null;
    	FileOutputStream        fos = null;
    		    		
		is = new URL(url).openStream();
    	fos = new FileOutputStream ( demofile+".part");

    	byte[]  buffer = new byte [4096];
    	
    	int totalcount =0;
    	
    	long tprint = SystemClock.uptimeMillis();
    	int partialcount = 0;
    	
    	while(true){
    		

    		int count = is.read (buffer);

    	    if ( count<=0 ) break;
    	    fos.write (buffer, 0, count);
    	    
    	    totalcount += count;
    	    partialcount += count;
    	    
    	    long tnow =  SystemClock.uptimeMillis();
    	    if ( (tnow-tprint)> 1000) {
    	    	
    	    	float size_MB = totalcount * (1.0f/(1<<20));
    	    	float speed_KB = partialcount  * (1.0f/(1<<10)) * ((tnow-tprint)/1000.0f);
    	    	
    	    	publishProgress( String.format("downloaded %.1f MB (%.1f KB/sec)",
    	    			size_MB, speed_KB));

    	    	tprint = tnow;
    	    	partialcount = 0;
    	    	
    	    }
    	       	    
    	   
    	}
    	
    	is.close();
    	fos.close();
    	

    	new File(demofile+".part" )
    		.renameTo(new File(demofile));
    	
    	// done
    	publishProgress("download done" );
    	
		SystemClock.sleep(2000);
    	
	}
	
	private void extract_data() throws Exception{
		Log.i("DownloadTask.java", "extracting PAK data");

		ZipFile file = new ZipFile  (demofile);
		extract_file( file, "ID1/PAK0.PAK", pakfile);

    	file.close();
    	
    	// done
    	publishProgress("extract done" );

		SystemClock.sleep(1000);

		isDownloading = false;
	}

	private void extract_file( ZipFile file, String entry_name, String output_name ) throws Exception{

		Log.i( "DownloadTask.java", "extracting " + entry_name + " to " + output_name);

		String short_name = new File(output_name).getName();
		
	    // create output directory
		new File(output_name).getParentFile().mkdirs();
		
		ZipEntry entry = file.getEntry(entry_name);
		
		if ( entry.isDirectory() ){	
			Log.i( "DownloadTask.java", entry_name + " is a directory");
			new File(output_name).mkdir();
			return;
		}
		
				
       	InputStream is = null;
    	FileOutputStream  fos = null;
    		    		
		is = file.getInputStream(entry);
		
    	fos = new FileOutputStream ( output_name+".part" );

    	byte[]  buffer = new byte [4096];
    	
    	int totalcount =0;
    	
    	long tprint = SystemClock.uptimeMillis();
    	
    	while(true){

    		int count = is.read (buffer);
    		//Log.i( "DownloadTask.java", "extracted " + count + " bytes");
    		
    	    if ( count<=0 ) break;
    	    fos.write (buffer, 0, count);
    	    
    	    totalcount += count;
    	    
    	    long tnow =  SystemClock.uptimeMillis();
    	    if ( (tnow-tprint)> 1000) {
    	    	
    	    	float size_MB = totalcount * (1.0f/(1<<20));
    	    	
    	    	publishProgress( String.format("%s : extracted %.1f MB",
    	    			short_name, size_MB));

    	    	tprint = tnow;
    	    }
    	}

    	is.close();
    	fos.close();
    	    	
    	/// rename part file
    	
    	new File(output_name+".part" )
    		.renameTo(new File(output_name));
    	
    	// done
    	publishProgress( String.format("%s : done.",
    			short_name));
	}
	
	@Override
	protected Void doInBackground(Void... unused) {
		
    	try {

			//Inform game we are now downloading
			QVRJNILib.setDownloadStatus(2);

    		long t = SystemClock.uptimeMillis();

    		download_demo();
    		
    		extract_data();   		
    		
    		t = SystemClock.uptimeMillis() - t;

    		Log.i( "DownloadTask.java", "done in " + t + " ms");
	    	
    	} catch (Exception e) {

			e.printStackTrace();

			QVRJNILib.setDownloadStatus(0);
			publishProgress("Error: " + e );
		}
    	
		return(null);
	}
	
	@Override
	protected void onProgressUpdate(String... progress) {
		Log.i( "DownloadTask.java", progress[0]);
		this.progress = progress[0];
	}
	
	@Override
	protected void onPostExecute(Void unused) {
		File f = new File(QVRConfig.GetFullWorkingFolder() + "id1/pak0.pak");
		if (f.exists()) {
			QVRJNILib.setDownloadStatus(1);
		} else
		{
			QVRJNILib.setDownloadStatus(0);
		}
	}
}
