package org.stingle.photos.Sync;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.AsyncTasks.Sync.DownloadThumbsAsyncTask;
import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncSteps.FSSync;
import org.stingle.photos.Sync.SyncSteps.ImportMedia;
import org.stingle.photos.Sync.SyncSteps.SyncCloudToLocalDb;
import org.stingle.photos.Sync.SyncSteps.UploadToCloud;
import org.stingle.photos.Util.Helpers;

import java.lang.ref.WeakReference;

public class SyncAsyncTask extends AsyncTask<Void, Void, Boolean> {

	public static SyncAsyncTask instance;
	private WeakReference<Context> context;
	private final OnAsyncTaskFinish onFinishListener;
	private int mode;

	static final public int MODE_FULL = 0;
	static final public int MODE_IMPORT_AND_UPLOAD = 1;
	static final public int MODE_CLOUD_TO_LOCAL = 2;
	static final public int MODE_CLOUD_TO_LOCAL_AND_UPLOAD = 3;

	public SyncAsyncTask(Context context) {
		this(context, MODE_FULL, null);
	}

	public SyncAsyncTask(Context context, int mode) {
		this(context, mode, null);
	}

	public SyncAsyncTask(Context context, OnAsyncTaskFinish onFinishListener) {
		this(context, MODE_FULL, onFinishListener);
	}

	public SyncAsyncTask(Context context, int mode, OnAsyncTaskFinish onFinishListener) {
		instance = this;
		this.context = new WeakReference<>(context);;
		this.mode = mode;
		this.onFinishListener = onFinishListener;
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		Context myContext = context.get();
		if(myContext == null){
			return false;
		}

		if(!LoginManager.isLoggedIn(myContext)){
			return false;
		}

		startSync(myContext);

		return true;
	}

	private void startSync(Context context){
		switch (mode){
			case MODE_FULL:
				syncCloudToLocalDb(context);
				FSSync(context);
				autoImport(context);
				upload(context);
				downloadThumbs(context);
				break;
			case MODE_IMPORT_AND_UPLOAD:
				autoImport(context);
				upload(context);
				break;
			case MODE_CLOUD_TO_LOCAL:
				syncCloudToLocalDb(context);
				break;
			case MODE_CLOUD_TO_LOCAL_AND_UPLOAD:
				syncCloudToLocalDb(context);
				upload(context);
				break;
		}


		if(!isCancelled() && StinglePhotosApplication.syncRestartAfterFinish){
			StinglePhotosApplication.syncRestartAfterFinish = false;
			mode = StinglePhotosApplication.syncRestartAfterFinishMode;
			StinglePhotosApplication.syncRestartAfterFinishMode = MODE_FULL;
			startSync(context);
		}
	}

	public void FSSync(Context context){
		boolean needToUpdateUI = FSSync.sync(context);
		if(needToUpdateUI){
			LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent("REFRESH_GALLERY"));
		}
	}

	public void autoImport(Context context){
		(new ImportMedia(context,  this)).importMedia();
	}

	public void syncCloudToLocalDb(Context context){
		boolean needToUpdateUI = (new SyncCloudToLocalDb(context)).sync();
		if (needToUpdateUI){
			LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent("REFRESH_GALLERY"));
		}
	}

	public void upload(Context context){
		(new UploadToCloud(context,  this)).upload();

	}

	public void downloadThumbs(Context context){
		boolean isThumbsDwnIsDone = Helpers.getPreference(context, DownloadThumbsAsyncTask.PREF_IS_DWN_THUMBS_IS_DONE, false);
		StinglePhotosApplication app = (StinglePhotosApplication) context.getApplicationContext();
		if(!isThumbsDwnIsDone && app.downloadThumbsAsyncTask == null){
			app.downloadThumbsAsyncTask = new DownloadThumbsAsyncTask(context, new SyncManager.OnFinish() {
				@Override
				public void onFinish(Boolean needToUpdateUI) {
					app.downloadThumbsAsyncTask = null;
				}
			});
			app.downloadThumbsAsyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		}
	}

	public static void killDownloadThumbs(Context context){
		StinglePhotosApplication app = (StinglePhotosApplication) context.getApplicationContext();
		if(app.downloadThumbsAsyncTask != null && !app.downloadThumbsAsyncTask.isCancelled()){
			app.downloadThumbsAsyncTask.cancel(true);
		}
	}

	@Override
	protected void onPostExecute(Boolean result) {
		super.onPostExecute(result);

		if(onFinishListener != null) {
			if (result) {
				onFinishListener.onFinish();
			} else {
				onFinishListener.onFail();
			}
		}
		instance = null;

	}
}
