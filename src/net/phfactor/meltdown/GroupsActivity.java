package net.phfactor.meltdown;

import java.util.List;

import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.TwoLineListItem;

public class GroupsActivity extends ListActivity 
{
	static final String TAG = "MeltdownGA";
    /**
     * Custom list adapter that fits our rss data into the list.
     */
    private GroupListAdapter mAdapter;
	
	private MeltdownApp app;
	
	private ProgressDialog pd;
	private RestClient rc;
	private Context ctx;
	// Number of RSS items to pull at startup. Quantum on server seems to be 50 FYI.
	private int NUM_ITEMS = 300; 
	private int MAX_PROGRESS = NUM_ITEMS + 2; // For progress dialog. Crude? Why yes it is.
	
	@Override
	public void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);

		rc = new RestClient(this);
		app = (MeltdownApp) this.getApplicationContext();
		ctx = this;
		
		// TODO run setup if login errors out?
		// Check for login, run prefs
		if (rc.haveSetup() == false)
		{
			startActivity(new Intent(this, SetupActivity.class));
			Toast.makeText(this, "Please configure a server", Toast.LENGTH_SHORT).show();
			return;
		}

		setContentView(R.layout.list);
		
		doRefresh();
	}
	
	public void doRefresh()
	{
		pd = new ProgressDialog(this);
		
		app.clearAllData();
		
		pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		pd.setIndeterminate(false);
		pd.setMax(MAX_PROGRESS);
		pd.setMessage("Fetching groups, feeds & items...");
		pd.show();

		class GGTask extends AsyncTask<Void, Void, Void> 
		{
			long tzero, tend;
			
			@Override
			protected void onPreExecute() 
			{
				super.onPreExecute();
				tzero = System.currentTimeMillis();
			}
			protected Void doInBackground(Void... args) 
			{
				app.saveGroupsData(rc.fetchGroups());
				pd.setProgress(1);
				app.saveFeedsData(rc.fetchFeeds());
				pd.setProgress(2);
				// TODO Add Runnable upate-on-the-fly code from RssReader.java
				// FIXME limit fetch limit w/prefs-set bound, e.g. 100. 
				int item_count = NUM_ITEMS;
				while (item_count > 0)
				{
					item_count -= app.saveItemsData(rc.fetchSomeItems(app.getMax_read_id()));
					pd.setProgress(2 + (NUM_ITEMS - item_count));
				}
//				while (app.saveItemsData(rc.fetchSomeFeeds(app.getMaxFetchedId())) > 0) 
//					Log.i(TAG, "Pulling another chunk...");
				
				return null;
			}
			@Override
			protected void onPostExecute(Void arg) {
				pd.dismiss();
				
				tend = System.currentTimeMillis();
				double elapsed = (tend - tzero) / 1000.0;
				Log.d(TAG, String.format("%3.1f seconds to retrieve %d items", elapsed, NUM_ITEMS));
				
				mAdapter = new GroupListAdapter(GroupsActivity.this, app.getGroups());
				setListAdapter(mAdapter);
		        final ListView lv = getListView();

		        // TODO Display already-read on long click?
		        lv.setOnItemClickListener(new OnItemClickListener()
		        {
		        	@Override
		        	public void onItemClick(AdapterView<?> arg0, View view, int pos, long id)
		        	{
						RssGroup group = (RssGroup) lv.getItemAtPosition(pos);
						
						Intent intent = new Intent(GroupsActivity.this, ItemsActivity.class);
						Bundle bundle = new Bundle();
						bundle.putString("title", group.title);
						bundle.putInt("group_id", group.id);
						intent.putExtras(bundle);
						startActivity(intent);
		        	}
		        	
		        });
			}
		}

		new GGTask().execute();
		
		// TODO Change title to include number of unread items?
	}
	

    /**
     * ArrayAdapter encapsulates a java.util.List of T, for presentation in a
     * ListView. This subclass specializes it to hold RssItems and display
     * their title/description data in a TwoLineListItem.
     */
    private class GroupListAdapter extends ArrayAdapter<RssGroup> 
    {
        private LayoutInflater mInflater;

        public GroupListAdapter(Context context, List<RssGroup> objects) 
        {
            super(context, 0, objects);
            mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        /**
         * This is called to render a particular item for the on screen list.
         * Uses an off-the-shelf TwoLineListItem view, which contains text1 and
         * text2 TextViews. We pull data from the RssItem and set it into the
         * view. The convertView is the view from a previous getView(), so
         * we can re-use it.
         * 
         * @see ArrayAdapter#getView
         */
        @Override
        public View getView(int position, View convertView, ViewGroup parent) 
        {
            TwoLineListItem view;

            // Here view may be passed in for re-use, or we make a new one.
            if (convertView == null) 
            {
                view = (TwoLineListItem) mInflater.inflate(android.R.layout.simple_list_item_2,
                        null);
            } else 
            {
                view = (TwoLineListItem) convertView;
            }

            RssGroup grp = app.getGroups().get(position);

            // Set the item title and description into the view.
            view.getText1().setText(grp.title);
            int unread_count = app.unreadItemCount(grp.id);
            String descr = "";
            if (unread_count == 0)
            	descr = " -- No unread items --";
            else if (unread_count == 1)
            	descr = "One unread item";
            else
            	descr = String.format("%d unread items", unread_count);
            view.getText2().setText(descr);
            return view;
        }
    }
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) 
	{
		switch (item.getItemId())
		{
		case R.id.menu_refresh:
			Log.d(TAG, "Refreshing...");
			doRefresh();
			return true;
		case R.id.menu_settings:
			Log.d(TAG, "Settings selecected");
			startActivity(new Intent(this, SetupActivity.class));
			return true;
		}
		return false;
	}



	@Override
	public boolean onCreateOptionsMenu(Menu menu) 
	{
		getMenuInflater().inflate(R.menu.activity_groups, menu);
		return true;
	}
}
