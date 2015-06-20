/*
 * Copyright 2015 marlonlom
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package co.malm.heusum.mobile.activities;

import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.MediaRouteActionProvider;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.RemoteMediaPlayer;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import java.io.IOException;

import butterknife.ButterKnife;
import butterknife.InjectView;
import co.malm.heusum.mobile.R;

public class HomeActivity extends AppCompatActivity {

    @InjectView(R.id.app_toolbar)
    Toolbar mToolbar;
    @InjectView(R.id.cast_button)
    Button mButton;

    private MediaRouter mMediaRouter;
    private MediaRouteSelector mMediaRouteSelector;
    private MediaRouter.Callback mMediaRouterCallback;
    private CastDevice mSelectedDevice;
    private GoogleApiClient mApiClient;
    private RemoteMediaPlayer mRemoteMediaPlayer;
    private Cast.Listener mCastClientListener;
    private boolean mWaitingForReconnect = false;
    private boolean mApplicationStarted = false;
    private boolean mVideoIsLoaded;
    private boolean mIsPlaying;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        ButterKnife.inject(this);
        setupToolbar();
        setupCastButton();
        initMediaRouter();
    }

    private void setupCastButton() {
        mButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mVideoIsLoaded)
                    startVideo();
                else
                    controlVideo();
            }
        });
    }

    private void controlVideo() {
        if (mRemoteMediaPlayer == null || !mVideoIsLoaded)
            return;

        if (mIsPlaying) {
            mRemoteMediaPlayer.pause(mApiClient);
            mButton.setText(getString(R.string.resume_video));
        } else {
            mRemoteMediaPlayer.play(mApiClient);
            mButton.setText(getString(R.string.pause_video));
        }
    }

    private void startVideo() {
        MediaMetadata mediaMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
        mediaMetadata.putString(MediaMetadata.KEY_TITLE, getString(R.string.video_title));

        MediaInfo mediaInfo = new MediaInfo.Builder(getString(R.string.video_url))
                .setContentType(getString(R.string.content_type_mp4))
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setMetadata(mediaMetadata)
                .build();
        try {
            if (mApiClient == null) {
                launchReceiver();
                return;
            }
            mRemoteMediaPlayer.load(mApiClient, mediaInfo, true)
                    .setResultCallback(new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
                        @Override
                        public void onResult(RemoteMediaPlayer.MediaChannelResult mediaChannelResult) {
                            Log.d("Chromecastdemo", mediaChannelResult.getStatus().toString());
                            if (mediaChannelResult.getStatus().isSuccess()) {
                                mVideoIsLoaded = true;
                                mButton.setText(getString(R.string.pause_video));
                            } else {
                                Toast.makeText(HomeActivity.this, "fail ?", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        } catch (Exception e) {
            Log.e("Chromecastdemo", e.getMessage());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.menu_home, menu);
        MenuItem mediaRouteMenuItem = menu.findItem(R.id.media_route_menu_item);
        MediaRouteActionProvider mediaRouteActionProvider = (MediaRouteActionProvider) MenuItemCompat.getActionProvider(mediaRouteMenuItem);
        mediaRouteActionProvider.setRouteSelector(mMediaRouteSelector);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            Toast.makeText(this, R.string.action_settings, Toast.LENGTH_SHORT).show();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setupToolbar() {
        setSupportActionBar(mToolbar);
    }

    /**
     * Configure Cast device discovery
     */
    private void initMediaRouter() {
        mMediaRouter = MediaRouter.getInstance(getApplicationContext());
        final String categoryForCast = CastMediaControlIntent.categoryForCast(getString(R.string.app_id));
        mMediaRouteSelector = new MediaRouteSelector.Builder().addControlCategory(categoryForCast).build();
        mMediaRouterCallback = new MediaRouterCallback();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Start media router discovery
        mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);
    }

    private void initRemoteMediaPlayer() {
        mRemoteMediaPlayer = new RemoteMediaPlayer();
        mRemoteMediaPlayer.setOnStatusUpdatedListener(new RemoteMediaPlayer.OnStatusUpdatedListener() {
            @Override
            public void onStatusUpdated() {
                MediaStatus mediaStatus = mRemoteMediaPlayer.getMediaStatus();
                mIsPlaying = mediaStatus.getPlayerState() == MediaStatus.PLAYER_STATE_PLAYING;
            }
        });

        mRemoteMediaPlayer.setOnMetadataUpdatedListener(new RemoteMediaPlayer.OnMetadataUpdatedListener() {
            @Override
            public void onMetadataUpdated() {
            }
        });
    }

    private void launchReceiver() {
        Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions.builder(mSelectedDevice, mCastClientListener);

        ConnectionCallbacks mConnectionCallbacks = new ConnectionCallbacks();
        ConnectionFailedListener mConnectionFailedListener = new ConnectionFailedListener();
        mApiClient = new GoogleApiClient.Builder(this)
                .addApi(Cast.API, apiOptionsBuilder.build())
                .addConnectionCallbacks(mConnectionCallbacks)
                .addOnConnectionFailedListener(mConnectionFailedListener)
                .build();

        mApiClient.connect();
    }

    private void teardown() {
        if (mApiClient != null) {
            if (mApplicationStarted) {
                try {
                    Cast.CastApi.stopApplication(mApiClient);
                    if (mRemoteMediaPlayer != null) {
                        Cast.CastApi.removeMessageReceivedCallbacks(mApiClient, mRemoteMediaPlayer.getNamespace());
                        mRemoteMediaPlayer = null;
                    }
                } catch (IOException e) {
                    //Log.e( TAG, "Exception while removing application " + e );
                }
                mApplicationStarted = false;
            }
            if (mApiClient.isConnected())
                mApiClient.disconnect();
            mApiClient = null;
        }
        mSelectedDevice = null;
        mVideoIsLoaded = false;
    }

    private void initCastClientListener() {
        mCastClientListener = new Cast.Listener() {
            @Override
            public void onApplicationStatusChanged() {
            }

            @Override
            public void onVolumeChanged() {
            }

            @Override
            public void onApplicationDisconnected(int statusCode) {
                teardown();
            }
        };
    }

    private void reconnectChannels(Bundle hint) {
        if ((hint != null) && hint.getBoolean(Cast.EXTRA_APP_NO_LONGER_RUNNING)) {
            //Log.e( TAG, "App is no longer running" );
            teardown();
        } else {
            try {
                Cast.CastApi.setMessageReceivedCallbacks(mApiClient, mRemoteMediaPlayer.getNamespace(), mRemoteMediaPlayer);
            } catch (Exception e) {
                Log.e(this.getClass().getSimpleName(), "Exception while creating media channel ", e);
            }
        }
    }

    private class MediaRouterCallback extends MediaRouter.Callback {

        @Override
        public void onRouteSelected(MediaRouter router, MediaRouter.RouteInfo info) {
            initCastClientListener();
            initRemoteMediaPlayer();

            mSelectedDevice = CastDevice.getFromBundle(info.getExtras());

            launchReceiver();
        }

        @Override
        public void onRouteUnselected(MediaRouter router, MediaRouter.RouteInfo info) {
            teardown();
            mSelectedDevice = null;
            mButton.setText(getString(R.string.play_video));
            mVideoIsLoaded = false;
        }
    }

    private class ConnectionCallbacks implements GoogleApiClient.ConnectionCallbacks {

        @Override
        public void onConnected(Bundle hint) {
            if (mWaitingForReconnect) {
                mWaitingForReconnect = false;
                reconnectChannels(hint);
            } else {
                try {
                    Cast.CastApi.launchApplication(mApiClient, getString(R.string.app_id), false)
                            .setResultCallback(
                                    new ResultCallback<Cast.ApplicationConnectionResult>() {
                                        @Override
                                        public void onResult(
                                                Cast.ApplicationConnectionResult applicationConnectionResult) {
                                            Status status = applicationConnectionResult.getStatus();
                                            if (status.isSuccess()) {
                                                //Values that can be useful for storing/logic
                                                ApplicationMetadata applicationMetadata =
                                                        applicationConnectionResult.getApplicationMetadata();
                                                String sessionId =
                                                        applicationConnectionResult.getSessionId();
                                                String applicationStatus =
                                                        applicationConnectionResult.getApplicationStatus();
                                                boolean wasLaunched =
                                                        applicationConnectionResult.getWasLaunched();

                                                mApplicationStarted = true;
                                                reconnectChannels(null);
                                            }
                                        }
                                    }
                            );
                } catch (Exception e) {

                }
            }
        }

        @Override
        public void onConnectionSuspended(int i) {
            mWaitingForReconnect = true;
        }
    }

    private class ConnectionFailedListener implements GoogleApiClient.OnConnectionFailedListener {
        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            teardown();
        }
    }
}
