package com.liskovsoft.smartyoutubetv2.tv.ui.search.tags.vineyard;

import com.liskovsoft.smartyoutubetv2.tv.ui.search.tags.OnScreenKeyboard;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.leanback.app.RowsSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.RowPresenter.ViewHolder;
import androidx.leanback.widget.SpeechRecognitionCallback;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.helpers.PermissionHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.SearchTagsProvider;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.SearchData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.adapter.vineyard.PaginationAdapter;
import com.liskovsoft.smartyoutubetv2.tv.adapter.vineyard.TagAdapter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.CustomListRowPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.base.OnItemLongPressedListener;
import com.liskovsoft.smartyoutubetv2.tv.presenter.vineyard.TagPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.misc.ProgressBarManager;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.search.SearchSupportFragment;

import net.gotev.speech.GoogleVoiceTypingDisabledException;
import net.gotev.speech.Speech;
import net.gotev.speech.SpeechDelegate;
import net.gotev.speech.SpeechRecognitionNotAvailable;
import net.gotev.speech.SpeechUtil;

import java.util.ArrayList;
import java.util.List;

public abstract class SearchTagsFragmentBase extends SearchSupportFragment
        implements SearchSupportFragment.SearchResultProvider, SearchView {
    private static final String TAG = SearchTagsFragmentBase.class.getSimpleName();
    private static final int REQUEST_SPEECH = 0x00000010;

    private TagAdapter mSearchTagsAdapter;
    //private ObjectAdapter mItemResultsAdapter;
    private ArrayObjectAdapter mResultsAdapter; // contains tags adapter and results adapter (see attachAdapter method)
    private ListRowPresenter mResultsPresenter;
    private TagPresenter mTagsPresenter;

    private boolean mIsStopping;
    private SearchTagsProvider mSearchTagsProvider;
    private ProgressBarManager mProgressBarManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mProgressBarManager = new ProgressBarManager();
        mResultsPresenter = new CustomListRowPresenter();
        mResultsPresenter.enableChildRoundedCorners(getMainUIData().isUiTweakEnabled(MainUIData.UI_TWEAK_ROUNDED_CORNERS));
        mResultsAdapter = new ArrayObjectAdapter(mResultsPresenter);
        mTagsPresenter = new TagPresenter();
        mSearchTagsAdapter = new TagAdapter(getContext(), mTagsPresenter, "");
        setSearchResultProvider(this);
        setupListenersAndPermissions();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = super.onCreateView(inflater, container, savedInstanceState);

        mProgressBarManager.setRootView((ViewGroup) root);

        addOnScreenKeyboard(root);

        return root;
    }

    /**
     * YouNew: YouTube TV style search - on-screen keyboard on the left, results on the right.
     * Typing updates the suggestions; the Search key runs the search.
     */
    private void addOnScreenKeyboard(View root) {
        android.widget.FrameLayout searchFrame = root.findViewById(androidx.leanback.R.id.lb_search_frame);
        View results = root.findViewById(androidx.leanback.R.id.lb_results_frame);
        if (searchFrame == null || results == null) {
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        int start = (int) (48 * density);

        OnScreenKeyboard keyboard = new OnScreenKeyboard(getContext(), new OnScreenKeyboard.Listener() {
            @Override
            public void onTextChanged(String text) {
                setSearchQuery(text, false);
            }

            @Override
            public void onSearch(String text) {
                if (!text.trim().isEmpty()) {
                    setSearchQuery(text, true);
                }
            }
        });

        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = (int) (400 * density); // keyboard on the right, like YouTube TV
        lp.topMargin = (int) (110 * density);
        searchFrame.addView(keyboard, lp);

        // Results (suggestions and videos) go below the keyboard
        ViewGroup.MarginLayoutParams resultsLp = (ViewGroup.MarginLayoutParams) results.getLayoutParams();
        resultsLp.topMargin = (int) (160 * density); // the result rows have their own top offset
        results.setLayoutParams(resultsLp);

        // When you move down into the video rows they slide up over the keyboard's place, so hide the keyboard then.
        // The first row (suggestions like "cat tv", "cat") stays below the keyboard, so the keyboard shows there,
        // and on the search bar and the keys.
        root.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (newFocus == null || !keyboard.isAttachedToWindow()) {
                return;
            }

            boolean inLowerRows = isInside(newFocus, results) && getResultsRow(newFocus) > 0;
            keyboard.setVisibility(inLowerRows ? View.INVISIBLE : View.VISIBLE);
        });
    }

    /** Which results row (0 = first) holds the focused view, or -1 if it isn't in a row. */
    private int getResultsRow(View focused) {
        androidx.leanback.widget.VerticalGridView grid = getRowsSupportFragment() != null ? getRowsSupportFragment().getVerticalGridView() : null;
        if (grid == null) {
            return -1;
        }

        for (View current = focused; current != null && current.getParent() instanceof View; current = (View) current.getParent()) {
            if (current.getParent() == grid) {
                return grid.getChildAdapterPosition(current);
            }
        }

        return -1;
    }

    /** True if the view is the given parent or somewhere inside it. */
    private static boolean isInside(View view, View parent) {
        for (View current = view; current != null; current = current.getParent() instanceof View ? (View) current.getParent() : null) {
            if (current == parent) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void onStart() {
        super.onStart();
        mIsStopping = false;
    }

    @Override
    public void onStop() {
        super.onStop();
        mIsStopping = true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_SPEECH:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        setSearchQuery(data, true);
                        break;
                    case Activity.RESULT_CANCELED:
                        Log.i(TAG, "Recognizer canceled");
                        break;
                }
                break;
        }
    }

    @Override
    public ObjectAdapter getResultsAdapter() {
        return mResultsAdapter;
    }

    @Override
    public void showProgressBar(boolean show) {
        if (show) {
            mProgressBarManager.show();
        } else {
            mProgressBarManager.hide();
        }
    }

    protected abstract void onItemViewSelected(Object item);
    
    protected abstract void onItemViewClicked(Object item);

    protected void setSearchTagsProvider(SearchTagsProvider provider) {
        mSearchTagsProvider = provider;
    }

    protected void setSearchTagsLongPressListener(OnItemLongPressedListener listener) {
        mTagsPresenter.setOnItemViewLongPressedListener(listener);
    }

    public boolean isStopping() {
        return mIsStopping;
    }

    public boolean hasResults() {
        return mResultsAdapter.size() > 0;
    }

    @SuppressWarnings("deprecation")
    private void setupListenersAndPermissions() {
        setOnItemViewClickedListener((itemViewHolder, item, rowViewHolder, row) -> onItemViewClicked(item));
        setOnItemViewSelectedListener((itemViewHolder, item, rowViewHolder, row) -> onItemViewSelected(item));

        // All needed permissions acquired inside SearchBar component.
        // See: androidx.leanback.widget.SearchBar.startRecognition()
        //if (SpeechRecognizer.isRecognitionAvailable(getContext())) {
        //    PermissionHelpers.verifyMicPermissions(getContext());
        //}

        // NOTE: External recognizer makes voice search behave unexpectedly (broken by Google app updates).
        // You should avoid using it till there be a solution.

        switch (getSearchData().getSpeechRecognizerType()) {
            case SearchData.SPEECH_RECOGNIZER_SYSTEM:
                // Don't uncomment. Sometimes system recognizer works on lower api
                // Do nothing unless we have old api.
                // Internal recognizer needs API >= 23. See: androidx.leanback.widget.SearchBar.startRecognition()
                //if (Build.VERSION.SDK_INT < 23) {
                //    setSpeechRecognitionCallback(mDefaultCallback);
                //}
                break;
            case SearchData.SPEECH_RECOGNIZER_INTENT:
                setSpeechRecognitionCallback(mDefaultCallback);
                break;
            case SearchData.SPEECH_RECOGNIZER_GOTEV:
                Speech.init(getContext());
                setSpeechRecognitionCallback(mGotevCallback);
                break;
        }
    }

    protected void stopSpeechService() {
        // Note: Other services don't need to be stopped

        if (getSearchData().getSpeechRecognizerType() != SearchData.SPEECH_RECOGNIZER_GOTEV) {
            return;
        }

        try {
            Speech.getInstance().stopListening();
        } catch (IllegalArgumentException | NoSuchMethodError e) { // Speech service not registered/Android 4 (no such method)
            e.printStackTrace();
        }
    }

    protected void loadSearchTags(String searchQuery) {
        searchTaggedPosts(searchQuery);
    }

    private void searchTaggedPosts(String query) {
        mSearchTagsAdapter.setTag(query);
        mResultsAdapter.clear();
        mSearchTagsAdapter.clear();
        performTagSearch(mSearchTagsAdapter);
    }

    private void performTagSearch(TagAdapter adapter) {
        if (mSearchTagsProvider == null) {
            return;
        }

        String query = adapter.getAdapterOptions().get(PaginationAdapter.KEY_TAG);
        mSearchTagsProvider.search(query, results -> {
            adapter.addAllItems(results);
            attachAdapter(0, adapter);
            // Same suggestions in the keyboard
            //displayCompletions(toCompletions(results));
        });
    }

    private List<String> toCompletions(List<Tag> results) {
        List<String> result = null;

        if (results != null) {
            result = new ArrayList<>();

            for (Tag tag : results) {
                result.add(tag.tag);
            }
        }

        return result;
    }

    /**
     * Disable scrolling on partially updated rows. This prevent controls from misbehaving.
     */
    protected void freeze(boolean freeze) {
        // Disable scrolling on partially updated rows. This prevent controls from misbehaving.
        RowsSupportFragment rowsSupportFragment = getRowsSupportFragment();
        if (mResultsPresenter != null && rowsSupportFragment != null) {
            ViewHolder vh = rowsSupportFragment.getRowViewHolder(rowsSupportFragment.getSelectedPosition());
            if (vh != null) {
                mResultsPresenter.freeze(vh, freeze);
            }
        }
    }

    protected void attachAdapter(int index, ObjectAdapter adapter) {
        if (mResultsAdapter != null) {
            if (!containsAdapter(adapter)) {
                index = Math.min(index, mResultsAdapter.size());
                mResultsAdapter.add(index, new ListRow(adapter));
            }
        }
    }

    protected void attachAdapter(int index, HeaderItem header, ObjectAdapter adapter) {
        if (mResultsAdapter != null) {
            if (!containsAdapter(adapter)) {
                index = Math.min(index, mResultsAdapter.size());
                mResultsAdapter.add(index, new ListRow(header, adapter));
            }
        }
    }

    protected void detachAdapter(int index) {
        if (mResultsAdapter != null && index < mResultsAdapter.size()) {
            mResultsAdapter.removeItems(index, 1);
        }
    }

    protected void clearTags() {
        if (containsAdapter(mSearchTagsAdapter)) {
            detachAdapter(0);
        }
    }

    protected void removeTag(Tag tag) {
        if (containsAdapter(mSearchTagsAdapter)) {
            mSearchTagsAdapter.remove(tag);
            if (mSearchTagsAdapter.size() == 0) {
                detachAdapter(0);
            }
        }
    }

    protected boolean containsAdapter(ObjectAdapter adapter) {
        if (mResultsAdapter != null) {
            for (int i = 0; i < mResultsAdapter.size(); i++) {
                ListRow row = (ListRow) mResultsAdapter.get(i);
                if (row.getAdapter() == adapter) {
                    return true;
                }
            }
        }

        return false;
    }

    private MainUIData getMainUIData() {
        return MainUIData.instance(getContext());
    }

    private SearchData getSearchData() {
        return SearchData.instance(getContext());
    }

    @SuppressWarnings("deprecation")
    private final SpeechRecognitionCallback mDefaultCallback = () -> {
        if (isAdded()) {
            if (PermissionHelpers.hasMicPermissions(getContext())) {
                MessageHelpers.showMessage(getContext(), R.string.disable_mic_permission);
            }

            try {
                startActivityForResult(getRecognizerIntent(), REQUEST_SPEECH);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "Cannot find activity for speech recognizer", e);
            } catch (NullPointerException e) {
                Log.e(TAG, "Speech recognizer can't obtain applicationInfo", e);
            }
        } else {
            Log.e(TAG, "Can't perform search. Fragment is detached.");
        }
    };

    @SuppressWarnings("deprecation")
    private final SpeechRecognitionCallback mGotevCallback = () -> {
        if (isAdded()) {
            try {
                // you must have android.permission.RECORD_AUDIO granted at this point
                PermissionHelpers.verifyMicPermissions(getContext());
                Speech.getInstance().startListening(new SpeechDelegate() {
                    @Override
                    public void onStartOfSpeech() {
                        Log.i(TAG, "speech recognition is now active");
                        showListening();
                    }

                    @Override
                    public void onSpeechRmsChanged(float value) {
                        Log.d(TAG, "rms is now: " + value);
                    }

                    @Override
                    public void onSpeechPartialResults(List<String> results) {
                        StringBuilder str = new StringBuilder();
                        for (String res : results) {
                            str.append(res).append(" ");
                        }

                        String result = str.toString().trim();
                        Log.i(TAG, "partial result: " + result);
                        setSearchQuery(result, true);

                        showNotListening();
                    }

                    @Override
                    public void onSpeechResult(String result) {
                        Log.i(TAG, "result: " + result);
                        setSearchQuery(result, true);

                        showNotListening();
                    }
                });
            } catch (SpeechRecognitionNotAvailable | GoogleVoiceTypingDisabledException exc) {
                Log.e(TAG, "Speech recognition is not available on this device!");
                // You can prompt the user if he wants to install Google App to have
                // speech recognition, and then you can simply call:
                try {
                    SpeechUtil.redirectUserToGoogleAppOnPlayStore(getContext());
                } catch (ActivityNotFoundException | NullPointerException e) {
                    // NullPointerException: android.os.Parcel.readException (Parcel.java:1478)
                    e.printStackTrace();
                }
            }
        } else {
            Log.e(TAG, "Can't perform search. Fragment is detached.");
        }
    };
}