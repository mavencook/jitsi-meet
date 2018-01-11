/*
 * Copyright @ 2017-present Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jitsi.meet.sdk;

import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import java.net.URL;

import com.facebook.react.modules.core.DefaultHardwareBackBtnHandler;

/**
 * Base Activity for applications integrating Jitsi Meet at a higher level. It
 * contains all the required wiring between the {@code JKConferenceView} and
 * the Activity lifecycle methods already implemented.
 *
 * In this activity we use a single {@code JKConferenceView} instance. This
 * instance gives us access to a view which displays the welcome page and the
 * conference itself. All lifetime methods associated with this Activity are
 * hooked to the React Native subsystem via proxy calls through the
 * {@code JKConferenceView} static methods.
 */
public class JitsiMeetActivity extends AppCompatActivity {
    /**
     * The request code identifying requests for the permission to draw on top
     * of other apps. The value must be 16-bit and is arbitrarily chosen here.
     */
    private static final int OVERLAY_PERMISSION_REQUEST_CODE
        = (int) (Math.random() * Short.MAX_VALUE);

    /**
     * The default behavior of this {@code JitsiMeetActivity} upon invoking the
     * back button if {@link #view} does not handle the invocation.
     */
    private DefaultHardwareBackBtnHandler defaultBackButtonImpl;

    /**
     * The default base {@code URL} used to join a conference when a partial URL
     * (e.g. a room name only) is specified. The value is used only while
     * {@link #view} equals {@code null}.
     */
    private URL defaultURL;

    /**
     * Instance of the {@link JitsiMeetView} which this activity will display.
     */
    private JitsiMeetView view;

    /**
     * Whether Picture-in-Picture is available. The value is used only while
     * {@link #view} equals {@code null}.
     */
    private boolean pipAvailable;

    /**
     * Whether the Welcome page is enabled. The value is used only while
     * {@link #view} equals {@code null}.
     */
    private boolean welcomePageEnabled;

    private boolean canRequestOverlayPermission() {
        return
            BuildConfig.DEBUG
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && getApplicationInfo().targetSdkVersion
                    >= Build.VERSION_CODES.M;
    }

    /**
     *
     * @see JitsiMeetView#getDefaultURL()
     */
    public URL getDefaultURL() {
        return view == null ? defaultURL : view.getDefaultURL();
    }

    /**
     *
     * @see JitsiMeetView#getPictureInPictureAvailable()
     */
    public boolean getPictureInPictureAvailable() {
        return view == null
            ? pipAvailable : view.getPictureInPictureAvailable();
    }

    /**
     *
     * @see JitsiMeetView#getWelcomePageEnabled()
     */
    public boolean getWelcomePageEnabled() {
        return view == null ? welcomePageEnabled : view.getWelcomePageEnabled();
    }

    /**
     * Initializes the {@link #view} of this {@code JitsiMeetActivity} with a
     * new {@link JitsiMeetView} instance.
     */
    private void initializeContentView() {
        JitsiMeetView view = initializeView();

        if (view != null) {
            this.view = view;
            setContentView(this.view);
        }
    }

    /**
     * Initializes a new {@link JitsiMeetView} instance.
     *
     * @return a new {@code JitsiMeetView} instance.
     */
    protected JitsiMeetView initializeView() {
        JitsiMeetView view = new JitsiMeetView(this);

        // XXX Before calling JitsiMeetView#loadURL, make sure to call whatever
        // is documented to need such an order in order to take effect:
        view.setDefaultURL(defaultURL);
        view.setPictureInPictureAvailable(pipAvailable);
        view.setWelcomePageEnabled(welcomePageEnabled);

        view.loadURL(null);

        return view;
    }

    /**
     * Loads the given URL and displays the conference. If the specified URL is
     * null, the welcome page is displayed instead.
     *
     * @param url The conference URL.
     */
    public void loadURL(@Nullable URL url) {
        view.loadURL(url);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE
                && canRequestOverlayPermission()) {
            if (Settings.canDrawOverlays(this)) {
                initializeContentView();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (!JitsiMeetView.onBackPressed()) {
            // JitsiMeetView didn't handle the invocation of the back button.
            // Generally, an Activity extender would very likely want to invoke
            // Activity#onBackPressed(). For the sake of consistency with
            // JitsiMeetView and within the Jitsi Meet SDK for Android though,
            // JitsiMeetActivity does what JitsiMeetView would've done if it
            // were able to handle the invocation.
            if (defaultBackButtonImpl == null) {
                super.onBackPressed();
            } else {
                defaultBackButtonImpl.invokeDefaultOnBackPressed();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // In Debug builds React needs permission to write over other apps in
        // order to display the warning and error overlays.
        if (canRequestOverlayPermission() && !Settings.canDrawOverlays(this)) {
            Intent intent
                = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));

            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE);
            return;
        }

        initializeContentView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (view != null) {
            view.dispose();
            view = null;
        }

        JitsiMeetView.onHostDestroy(this);
    }

    @Override
    public void onNewIntent(Intent intent) {
        JitsiMeetView.onNewIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();

        defaultBackButtonImpl = new DefaultHardwareBackBtnHandlerImpl(this);
        JitsiMeetView.onHostResume(this, defaultBackButtonImpl);
    }

    @Override
    public void onPictureInPictureModeChanged(
            boolean isInPictureInPictureMode, Configuration newConfig) {
        JitsiMeetView.onPictureInPictureModeChanged(isInPictureInPictureMode);
    }

    @Override
    public void onStop() {
        super.onStop();

        JitsiMeetView.onHostPause(this);
        defaultBackButtonImpl = null;
    }

    /**
     *
     * @see JitsiMeetView#setDefaultURL(URL)
     */
    public void setDefaultURL(URL defaultURL) {
        if (view == null) {
            this.defaultURL = defaultURL;
        } else {
            view.setDefaultURL(defaultURL);
        }
    }

    /**
     *
     * @see JitsiMeetView#setPictureInPictureAvailable(boolean)
     */
    public void setPictureInPictureAvailable(boolean pipAvailable) {
        if (view == null) {
            this.pipAvailable = pipAvailable;
        } else {
            view.setPictureInPictureAvailable(pipAvailable);
        }
    }

    /**
     *
     * @see JitsiMeetView#setWelcomePageEnabled(boolean)
     */
    public void setWelcomePageEnabled(boolean welcomePageEnabled) {
        if (view == null) {
            this.welcomePageEnabled = welcomePageEnabled;
        } else {
            view.setWelcomePageEnabled(welcomePageEnabled);
        }
    }
}
