package org.wordpress.android.ui.comments;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.wordpress.android.R;
import org.wordpress.android.WordPress;
import org.wordpress.android.datasets.CommentTable;
import org.wordpress.android.models.Blog;
import org.wordpress.android.models.Comment;
import org.wordpress.android.models.CommentList;
import org.wordpress.android.models.CommentStatus;
import org.wordpress.android.models.FilterCriteria;
import org.wordpress.android.ui.EmptyViewMessageType;
import org.wordpress.android.ui.FilteredRecyclerView;
import org.wordpress.android.ui.prefs.AppPrefs;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.NetworkUtils;
import org.wordpress.android.util.ToastUtils;
import org.xmlrpc.android.ApiHelper;
import org.xmlrpc.android.ApiHelper.ErrorType;
import org.xmlrpc.android.XMLRPCFault;
import org.xmlrpc.android.pojo.Params;
import org.xmlrpc.android.pojo.Struct;

import java.util.ArrayList;
import java.util.List;

public class CommentsListFragment extends Fragment {

    interface OnCommentSelectedListener {
        void onCommentSelected(long commentId);
    }

    private boolean mIsUpdatingComments = false;
    private boolean mCanLoadMoreComments = true;
    boolean mHasAutoRefreshedComments = false;

    private final CommentStatus[] commentStatuses = {CommentStatus.UNKNOWN, CommentStatus.UNAPPROVED,
            CommentStatus.APPROVED, CommentStatus.TRASH, CommentStatus.SPAM};

    private EmptyViewMessageType mEmptyViewMessageType = EmptyViewMessageType.NO_CONTENT;
    private FilteredRecyclerView mFilteredCommentsView;
    private CommentAdapter mAdapter;
    private ActionMode mActionMode;
    private CommentStatus mCommentStatusFilter;

    private UpdateCommentsTask mUpdateCommentsTask;

    public static final int COMMENTS_PER_PAGE = 30;

    private CommentAdapter getAdapter() {
        if (mAdapter == null) {
             // called after comments have been loaded
            CommentAdapter.OnDataLoadedListener dataLoadedListener = new CommentAdapter.OnDataLoadedListener() {
                @Override
                public void onDataLoaded(boolean isEmpty) {
                    if (!isAdded()) return;

                    if (!isEmpty) {
                        // Hide the empty view if there are already some displayed comments
                        mFilteredCommentsView.hideEmptyView();
                    } else if (!mIsUpdatingComments) {
                        // Change LOADING to NO_CONTENT message
                        mFilteredCommentsView.updateEmptyView(EmptyViewMessageType.NO_CONTENT);
                    }
                }
            };

            // adapter calls this to request more comments from server when it reaches the end
            CommentAdapter.OnLoadMoreListener loadMoreListener = new CommentAdapter.OnLoadMoreListener() {
                @Override
                public void onLoadMore() {
                    if (mCanLoadMoreComments && !mIsUpdatingComments) {
                        updateComments(true);
                    }
                }
            };

            // adapter calls this when selected comments have changed (CAB)
            CommentAdapter.OnSelectedItemsChangeListener changeListener = new CommentAdapter.OnSelectedItemsChangeListener() {
                @Override
                public void onSelectedItemsChanged() {
                    if (mActionMode != null) {
                        if (getSelectedCommentCount() == 0) {
                            mActionMode.finish();
                        } else {
                            updateActionModeTitle();
                            // must invalidate to ensure onPrepareActionMode is called
                            mActionMode.invalidate();
                        }
                    }
                }
            };

            CommentAdapter.OnCommentPressedListener pressedListener = new CommentAdapter.OnCommentPressedListener() {
                @Override
                public void onCommentPressed(int position, View view) {
                    Comment comment = getAdapter().getItem(position);
                    if (comment == null) {
                        return;
                    }
                    if (mActionMode == null) {
                        if (!getAdapter().isModeratingCommentId(comment.commentID)) {
                            mFilteredCommentsView.invalidate();
                            if (getActivity() instanceof OnCommentSelectedListener) {
                                ((OnCommentSelectedListener) getActivity()).onCommentSelected(comment.commentID);
                            }
                        }
                    } else {
                        getAdapter().toggleItemSelected(position, view);
                    }
                }
                @Override
                public void onCommentLongPressed(int position, View view) {
                    // enable CAB if it's not already enabled
                    if (mActionMode == null) {
                        if (getActivity() instanceof AppCompatActivity) {
                            ((AppCompatActivity) getActivity()).startSupportActionMode(new ActionModeCallback());
                            getAdapter().setEnableSelection(true);
                            getAdapter().setItemSelected(position, true, view);
                        }
                    } else {
                        getAdapter().toggleItemSelected(position, view);
                    }
                }
            };

            mAdapter = new CommentAdapter(getActivity(), WordPress.getCurrentLocalTableBlogId());
            mAdapter.setOnCommentPressedListener(pressedListener);
            mAdapter.setOnDataLoadedListener(dataLoadedListener);
            mAdapter.setOnLoadMoreListener(loadMoreListener);
            mAdapter.setOnSelectedItemsChangeListener(changeListener);
        }

        return mAdapter;
    }

    private boolean hasAdapter() {
        return (mAdapter != null);
    }

    private int getSelectedCommentCount() {
        return getAdapter().getSelectedCommentCount();
    }

    public void removeComment(Comment comment) {
        if (hasAdapter() && comment != null) {
            getAdapter().removeComment(comment);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Bundle extras = getActivity().getIntent().getExtras();
        if (extras != null) {
            mHasAutoRefreshedComments = extras.getBoolean(CommentsActivity.KEY_AUTO_REFRESHED);
            mEmptyViewMessageType = EmptyViewMessageType.getEnumFromString(extras.getString(
                    CommentsActivity.KEY_EMPTY_VIEW_MESSAGE));
        } else {
            mHasAutoRefreshedComments = false;
            mEmptyViewMessageType = EmptyViewMessageType.NO_CONTENT;
        }

        if (!NetworkUtils.isNetworkAvailable(getActivity())) {
            mFilteredCommentsView.updateEmptyView(EmptyViewMessageType.NETWORK_ERROR);
            return;
        }

        // Restore the empty view's message
        mFilteredCommentsView.updateEmptyView(mEmptyViewMessageType);

        if (!mHasAutoRefreshedComments) {
            updateComments(false);
            mFilteredCommentsView.setRefreshing(true);
            mHasAutoRefreshedComments = true;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.comment_list_fragment, container, false);

        mFilteredCommentsView = (FilteredRecyclerView) view.findViewById(R.id.filtered_recycler_view);
        mFilteredCommentsView.setLogT(AppLog.T.COMMENTS);
        mFilteredCommentsView.setFilterListener(new FilteredRecyclerView.FilterListener() {
            @Override
            public List<FilterCriteria> onLoadFilterCriteriaOptions(boolean refresh) {
                ArrayList<FilterCriteria> criterias = new ArrayList();
                for (CommentStatus criteria : commentStatuses) {
                    criterias.add(criteria);
                }
                return criterias;
            }

            @Override
            public void onLoadFilterCriteriaOptionsAsync(FilteredRecyclerView.FilterCriteriaAsyncLoaderListener listener, boolean refresh) {
                //noop
            }

            @Override
            public void onLoadData() {
                updateComments(false);
            }

            @Override
            public void onFilterSelected(int position, FilterCriteria criteria) {
                //trackCommentsAnalytics();
                AppPrefs.setCommentsStatusFilter((CommentStatus) criteria);
                mCommentStatusFilter = (CommentStatus) criteria;
            }

            @Override
            public FilterCriteria onRecallSelection() {
                mCommentStatusFilter = AppPrefs.getCommentsStatusFilter();
                return mCommentStatusFilter;
            }

            @Override
            public String onShowEmptyViewMessage(EmptyViewMessageType emptyViewMsgType) {

                if (emptyViewMsgType == EmptyViewMessageType.NO_CONTENT) {
                    FilterCriteria filter = mFilteredCommentsView.getCurrentFilter();
                    if (filter == null || CommentStatus.UNKNOWN.equals(filter)) {
                        return getString(R.string.comments_empty_list);
                    } else {
                        switch (mCommentStatusFilter) {
                            case APPROVED:
                                return getString(R.string.comments_empty_list_filtered_approved);
                            case UNAPPROVED:
                                return getString(R.string.comments_empty_list_filtered_pending);
                            case SPAM:
                                return getString(R.string.comments_empty_list_filtered_spam);
                            case TRASH:
                                return getString(R.string.comments_empty_list_filtered_trashed);
                            default:
                                return getString(R.string.comments_empty_list);
                        }
                    }

                } else {
                    int stringId = 0;
                    switch (emptyViewMsgType) {
                        case LOADING:
                            stringId = R.string.comments_fetching;
                            break;
                        case NETWORK_ERROR:
                            stringId = R.string.no_network_message;
                            break;
                        case PERMISSION_ERROR:
                            stringId = R.string.error_refresh_unauthorized_comments;
                            break;
                        case GENERIC_ERROR:
                            stringId = R.string.error_refresh_comments;
                            break;
                    }
                    return getString(stringId);
                }

            }

            @Override
            public void onShowCustomEmptyView(EmptyViewMessageType emptyViewMsgType) {
                //noop
            }
        });

        // the following will change the look and feel of the toolbar to match the current design
        mFilteredCommentsView.setToolbarBackgroundColor(getResources().getColor(R.color.blue_medium));
        mFilteredCommentsView.setToolbarSpinnerTextColor(getResources().getColor(R.color.white));
        mFilteredCommentsView.setToolbarSpinnerDrawable(R.drawable.arrow);
        mFilteredCommentsView.setToolbarLeftAndRightPadding(
                getResources().getDimensionPixelSize(R.dimen.margin_filter_spinner),
                getResources().getDimensionPixelSize(R.dimen.margin_none));

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mFilteredCommentsView.getAdapter() == null) {
            mFilteredCommentsView.setAdapter(getAdapter());
            if (!NetworkUtils.isNetworkAvailable(getActivity())){
                ToastUtils.showToast(getActivity(), getString(R.string.error_refresh_comments_showing_older));
            }
            getAdapter().loadComments(mCommentStatusFilter);
        }
    }

    public void setCommentStatusFilter(CommentStatus statusfilter) {
        mCommentStatusFilter = statusfilter;
    }

    private void dismissDialog(int id) {
        if (!isAdded())
            return;
        try {
            getActivity().dismissDialog(id);
        } catch (IllegalArgumentException e) {
            // raised when dialog wasn't created
        }
    }

    private void moderateSelectedComments(final CommentStatus newStatus) {
        final CommentList selectedComments = getAdapter().getSelectedComments();
        final CommentList updateComments = new CommentList();

        // build list of comments whose status is different than passed
        for (Comment comment: selectedComments) {
            if (comment.getStatusEnum() != newStatus)
                updateComments.add(comment);
        }
        if (updateComments.size() == 0) return;

        if (!NetworkUtils.checkConnection(getActivity())) return;

        final int dlgId;
        switch (newStatus) {
            case APPROVED:
                dlgId = CommentDialogs.ID_COMMENT_DLG_APPROVING;
                break;
            case UNAPPROVED:
                dlgId = CommentDialogs.ID_COMMENT_DLG_UNAPPROVING;
                break;
            case SPAM:
                dlgId = CommentDialogs.ID_COMMENT_DLG_SPAMMING;
                break;
            case TRASH:
                dlgId = CommentDialogs.ID_COMMENT_DLG_TRASHING;
                break;
            default :
                return;
        }
        getActivity().showDialog(dlgId);

        CommentActions.OnCommentsModeratedListener listener = new CommentActions.OnCommentsModeratedListener() {
            @Override
            public void onCommentsModerated(final CommentList moderatedComments) {
                if (!isAdded()) return;

                finishActionMode();
                dismissDialog(dlgId);
                if (moderatedComments.size() > 0) {
                    getAdapter().clearSelectedComments();
                    getAdapter().replaceComments(moderatedComments);
                } else {
                    ToastUtils.showToast(getActivity(), R.string.error_moderate_comment);
                }
            }
        };

        CommentActions.moderateComments(
                WordPress.getCurrentLocalTableBlogId(),
                updateComments,
                newStatus,
                listener);
    }

    private void confirmDeleteComments() {
        if (CommentStatus.TRASH.equals(mCommentStatusFilter)){
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(
                    getActivity());
            dialogBuilder.setTitle(getResources().getText(R.string.delete));
            int resid = getAdapter().getSelectedCommentCount() > 1 ? R.string.dlg_sure_to_delete_comments : R.string.dlg_sure_to_delete_comment;
            dialogBuilder.setMessage(getResources().getText(resid));
            dialogBuilder.setPositiveButton(getResources().getText(R.string.yes),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            deleteSelectedComments(true);
                        }
                    });
            dialogBuilder.setNegativeButton(
                    getResources().getText(R.string.no),
                    null);
            dialogBuilder.setCancelable(true);
            dialogBuilder.create().show();

        } else {

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setMessage(R.string.dlg_confirm_trash_comments);
            builder.setTitle(R.string.trash);
            builder.setCancelable(true);
            builder.setPositiveButton(R.string.trash_yes, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    deleteSelectedComments(false);
                }
            });
            builder.setNegativeButton(R.string.trash_no, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    dialog.cancel();
                }
            });
            AlertDialog alert = builder.create();
            alert.show();
        }
    }

    private void deleteSelectedComments(boolean deletePermanently) {
        if (!NetworkUtils.checkConnection(getActivity())) return;

        final int dlgId = deletePermanently ?  CommentDialogs.ID_COMMENT_DLG_DELETING : CommentDialogs.ID_COMMENT_DLG_TRASHING;

        final CommentList selectedComments = getAdapter().getSelectedComments();
        getActivity().showDialog(dlgId);
        CommentActions.OnCommentsModeratedListener listener = new CommentActions.OnCommentsModeratedListener() {
            @Override
            public void onCommentsModerated(final CommentList deletedComments) {
                if (!isAdded()) return;

                finishActionMode();
                dismissDialog(dlgId);
                if (deletedComments.size() > 0) {
                    getAdapter().clearSelectedComments();
                    getAdapter().deleteComments(deletedComments);
                } else {
                    ToastUtils.showToast(getActivity(), R.string.error_moderate_comment);
                }
            }
        };

        CommentStatus newStatus = CommentStatus.TRASH;
        if (deletePermanently){
            newStatus = CommentStatus.DELETE;
        }
        CommentActions.moderateComments(
                WordPress.getCurrentLocalTableBlogId(), selectedComments, newStatus, listener);
    }

    void loadComments() {
        // this is called from CommentsActivity when a comment was changed in the detail view,
        // and the change will already be in SQLite so simply reload the comment adapter
        // to show the change
        getAdapter().loadComments(mCommentStatusFilter);
    }

    void updateEmptyView(EmptyViewMessageType emptyViewMessageType){
        //this is called from CommentsActivity in the case the last moment for a given type has been changed from that
        //status, leaving the list empty, so we need to update the empty view. The method inside FilteredRecyclerView
        //does the handling itself, so we only check for null here.
        if (mFilteredCommentsView != null){
            mFilteredCommentsView.updateEmptyView(emptyViewMessageType);
        }
    }

    /*
     * get latest comments from server, or pass loadMore=true to get comments beyond the
     * existing ones
     */
    void updateComments(boolean loadMore) {
        if (mIsUpdatingComments) {
            AppLog.w(AppLog.T.COMMENTS, "update comments task already running");
            return;
        } else if (!NetworkUtils.isNetworkAvailable(getActivity())) {
            mFilteredCommentsView.updateEmptyView(EmptyViewMessageType.NETWORK_ERROR);
            mFilteredCommentsView.setRefreshing(false);
            ToastUtils.showToast(getActivity(), getString(R.string.error_refresh_comments_showing_older));
            //we're offline, load/refresh whatever we have in our local db
            getAdapter().loadComments(mCommentStatusFilter);
            return;
        }

        //immediately load/refresh whatever we have in our local db as we wait for the API call to get latest results
        if (!loadMore){
            getAdapter().loadComments(mCommentStatusFilter);
        }

        mFilteredCommentsView.updateEmptyView(EmptyViewMessageType.LOADING);

        mUpdateCommentsTask = new UpdateCommentsTask(loadMore, mCommentStatusFilter);
        mUpdateCommentsTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public void setCommentIsModerating(long commentId, boolean isModerating) {
        if (!hasAdapter()) return;

        if (isModerating) {
            getAdapter().addModeratingCommentId(commentId);
        } else {
            getAdapter().removeModeratingCommentId(commentId);
        }
    }

    public String getEmptyViewMessage() {
        return mEmptyViewMessageType.name();
    }

    /*
     * task to retrieve latest comments from server
     */
    private class UpdateCommentsTask extends AsyncTask<Void, Void, CommentList> {
        ErrorType mErrorType = ErrorType.NO_ERROR;
        final boolean mIsLoadingMore;
        final CommentStatus mStatusFilter;

        private UpdateCommentsTask(boolean loadMore, CommentStatus statusfilter) {
            mIsLoadingMore = loadMore;
            mStatusFilter = statusfilter;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mIsUpdatingComments = true;
            if (mIsLoadingMore) {
                mFilteredCommentsView.showLoadingProgress();
            }
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            mIsUpdatingComments = false;
            mUpdateCommentsTask = null;
            mFilteredCommentsView.setRefreshing(false);
        }

        @Override
        protected CommentList doInBackground(Void... args) {
            if (!isAdded()) {
                return null;
            }

            final Blog blog = WordPress.getCurrentBlog();
            if (blog == null) {
                mErrorType = ErrorType.INVALID_CURRENT_BLOG;
                return null;
            }

            Struct hPost = new Struct();
            if (mIsLoadingMore) {
                int numExisting = getAdapter().getItemCount();
                hPost.add("offset", numExisting);
                hPost.add("number", COMMENTS_PER_PAGE);
            } else {
                hPost.add("number", COMMENTS_PER_PAGE);
            }

            if (mStatusFilter != null){
                //if this is UNKNOWN that means show ALL, i.e., do not apply filter
                if (!mStatusFilter.equals(CommentStatus.UNKNOWN)){
                    hPost.add("status", CommentStatus.toString(mStatusFilter));
                }
            }

            Params params = new Params()
                    .add(blog.getRemoteBlogId())
                    .add(blog.getUsername())
                    .add(blog.getPassword())
                    .add(hPost);

            try {
                CommentList comments = ApiHelper.refreshComments(blog, params, new ApiHelper.DatabasePersistCallback() {
                    @Override
                    public void onDataReadyToSave(List list) {
                        int localBlogId = blog.getLocalTableBlogId();
                        CommentTable.deleteCommentsForBlogWithFilter(localBlogId, mStatusFilter);
                        CommentTable.saveComments(localBlogId, (CommentList)list);
                    }
                });
                return comments;
            } catch (XMLRPCFault xmlrpcFault) {
                mErrorType = ErrorType.UNKNOWN_ERROR;
                if (xmlrpcFault.getFaultCode() == 401) {
                    mErrorType = ErrorType.UNAUTHORIZED;
                }
            } catch (Exception e) {
                mErrorType = ErrorType.UNKNOWN_ERROR;
            }
            return null;
        }

        protected void onPostExecute(CommentList comments) {

            boolean isRefreshing = mFilteredCommentsView.isRefreshing();
            mIsUpdatingComments = false;
            mUpdateCommentsTask = null;

            if (!isAdded()) return;

            if (mIsLoadingMore) {
                mFilteredCommentsView.hideLoadingProgress();
            }
            mFilteredCommentsView.setRefreshing(false);

            if (isCancelled()) return;

            mCanLoadMoreComments = (comments != null && comments.size() > 0);

            // result will be null on error OR if no more comments exists
            if (comments == null && !getActivity().isFinishing() && mErrorType != ErrorType.NO_ERROR) {
                switch (mErrorType) {
                    case UNAUTHORIZED:
                        if (!mFilteredCommentsView.emptyViewIsVisible()) {
                            ToastUtils.showToast(getActivity(), getString(R.string.error_refresh_unauthorized_comments));
                        }
                        mFilteredCommentsView.updateEmptyView(EmptyViewMessageType.PERMISSION_ERROR);
                        return;
                    default:
                        ToastUtils.showToast(getActivity(), getString(R.string.error_refresh_comments));
                        mFilteredCommentsView.updateEmptyView(EmptyViewMessageType.GENERIC_ERROR);
                        return;
                }
            }

            if (!getActivity().isFinishing()) {
                if (comments != null && comments.size() > 0) {
                    getAdapter().loadComments(mStatusFilter);
                } else {
                    if (isRefreshing){
                        //if refreshing and no errors, we only want freshest stuff, so clear old data
                        getAdapter().clearComments();
                    }
                    mFilteredCommentsView.updateEmptyView(EmptyViewMessageType.NO_CONTENT);
                }
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (outState.isEmpty()) {
            outState.putBoolean("bug_19917_fix", true);
        }
        super.onSaveInstanceState(outState);
    }

    /****
     * Contextual ActionBar (CAB) routines
     ***/
    private void updateActionModeTitle() {
        if (mActionMode == null)
            return;
        int numSelected = getSelectedCommentCount();
        if (numSelected > 0) {
            mActionMode.setTitle(Integer.toString(numSelected));
        } else {
            mActionMode.setTitle("");
        }
    }

    private void finishActionMode() {
        if (mActionMode != null) {
            mActionMode.finish();
        }
    }

    private final class ActionModeCallback implements ActionMode.Callback {
        @Override
        public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
            mActionMode = actionMode;
            MenuInflater inflater = actionMode.getMenuInflater();
            inflater.inflate(R.menu.menu_comments_cab, menu);
            mFilteredCommentsView.setSwipeToRefreshEnabled(false);
            return true;
        }

        private void setItemEnabled(Menu menu, int menuId, boolean isEnabled) {
            final MenuItem item = menu.findItem(menuId);
            if (item == null || item.isEnabled() == isEnabled)
                return;
            item.setEnabled(isEnabled);
            if (item.getIcon() != null) {
                // must mutate the drawable to avoid affecting other instances of it
                Drawable icon = item.getIcon().mutate();
                icon.setAlpha(isEnabled ? 255 : 128);
                item.setIcon(icon);
            }
        }

        @Override
        public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
            final CommentList selectedComments = getAdapter().getSelectedComments();

            boolean hasSelection = (selectedComments.size() > 0);
            boolean hasApproved = hasSelection && selectedComments.hasAnyWithStatus(CommentStatus.APPROVED);
            boolean hasUnapproved = hasSelection && selectedComments.hasAnyWithStatus(CommentStatus.UNAPPROVED);
            boolean hasSpam = hasSelection && selectedComments.hasAnyWithStatus(CommentStatus.SPAM);
            boolean hasAnyNonSpam = hasSelection && selectedComments.hasAnyWithoutStatus(CommentStatus.SPAM);
            boolean hasTrash = hasSelection && selectedComments.hasAnyWithStatus(CommentStatus.TRASH);

            setItemEnabled(menu, R.id.menu_approve,   hasUnapproved || hasSpam || hasTrash);
            setItemEnabled(menu, R.id.menu_unapprove, hasApproved);
            setItemEnabled(menu, R.id.menu_spam,      hasAnyNonSpam);
            setItemEnabled(menu, R.id.menu_trash, hasSelection);

            final MenuItem trashItem = menu.findItem(R.id.menu_trash);
            if (trashItem != null){
                if (CommentStatus.TRASH.equals(mCommentStatusFilter)){
                    trashItem.setTitle(R.string.mnu_comment_delete_permanently);
                }
            }

            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
            int numSelected = getSelectedCommentCount();
            if (numSelected == 0)
                return false;

            int i = menuItem.getItemId();
            if (i == R.id.menu_approve) {
                moderateSelectedComments(CommentStatus.APPROVED);
                return true;
            } else if (i == R.id.menu_unapprove) {
                moderateSelectedComments(CommentStatus.UNAPPROVED);
                return true;
            } else if (i == R.id.menu_spam) {
                moderateSelectedComments(CommentStatus.SPAM);
                return true;
            } else if (i == R.id.menu_trash) {// unlike the other status changes, we ask the user to confirm trashing
                confirmDeleteComments();
                return true;
            } else {
                return false;
            }
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            getAdapter().setEnableSelection(false);
            mFilteredCommentsView.setSwipeToRefreshEnabled(true);
            mActionMode = null;
        }
    }

}
