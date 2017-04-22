package com.andreapivetta.blu.ui.tweetdetails

import android.app.Activity
import android.graphics.Typeface
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.andreapivetta.blu.R
import com.andreapivetta.blu.common.utils.*
import com.andreapivetta.blu.data.model.MetaData
import com.andreapivetta.blu.data.model.Tweet
import com.andreapivetta.blu.data.url.UrlInfo
import com.andreapivetta.blu.ui.custom.decorators.SpaceLeftItemDecoration
import com.andreapivetta.blu.ui.timeline.holders.BaseViewHolder
import com.andreapivetta.blu.ui.timeline.holders.ImagesAdapter
import com.luseen.autolinklibrary.AutoLinkTextView
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.tweet_big.view.*
import timber.log.Timber

/**
 * Created by andrea on 26/05/16.
 */
class StatusDetailsViewHolder(container: View, listener: DetailsInteractionListener) :
        BaseViewHolder(container, listener) {

    private val mediaViewStub = container.mediaViewStub
    private val quotedStatusViewStub = container.quotedStatusViewStub
    private val urlPreviewViewStub = container.urlPreviewViewStub
    private val shareImageButton = container.shareImageButton
    private val quoteImageButton = container.quoteImageButton

    private var inflatedMediaView: View? = null
    private var inflatedQuotedView: View? = null
    private var inflatedUrlPreviewView: View? = null

    override fun setup(tweet: Tweet) {
        val currentUser = tweet.user

        val listener = listener as DetailsInteractionListener

        userNameTextView.text = currentUser.name
        timeTextView.text = Utils.formatDate(tweet.timeStamp)

        (statusTextView as AutoLinkTextView).setupText(tweet.text)
        userScreenNameTextView.text = "@${currentUser.screenName}"

        var amount = "${tweet.favoriteCount}"
        var b = StyleSpan(Typeface.BOLD)

        var sb = SpannableStringBuilder(container.context.getString(R.string.likes, amount))
        sb.setSpan(b, 0, amount.length, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
        favouritesStatsTextView.text = sb

        amount = "${tweet.retweetCount}"
        b = StyleSpan(Typeface.BOLD)

        sb = SpannableStringBuilder(container.context.getString(R.string.retweets, amount))
        sb.setSpan(b, 0, amount.length, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
        retweetsStatsTextView.text = sb

        userProfilePicImageView.loadAvatar(currentUser.biggerProfileImageURL)

        if (tweet.favorited)
            favouriteImageButton.setImageResource(R.drawable.ic_favorite_red)
        else
            favouriteImageButton.setImageResource(R.drawable.ic_favorite)

        if (tweet.retweeted)
            retweetImageButton.setImageResource(R.drawable.ic_repeat_green)
        else
            retweetImageButton.setImageResource(R.drawable.ic_repeat)

        favouriteImageButton.setOnClickListener {
            if (tweet.favorited)
                listener.unfavorite(tweet)
            else
                listener.favorite(tweet)
        }

        retweetImageButton.setOnClickListener {
            if (tweet.retweeted)
                listener.unretweet(tweet)
            else
                listener.retweet(tweet)
        }

        userProfilePicImageView.setOnClickListener { listener.showUser(currentUser) }
        respondImageButton.setOnClickListener { listener.reply(tweet, currentUser) }
        shareImageButton.setOnClickListener { listener.shareTweet(tweet) }
        quoteImageButton.setOnClickListener { listener.quoteTweet(tweet) }

        when {
            tweet.hasSingleImage() -> setupPhoto(tweet)
            tweet.hasMultipleMedia() -> setupPhotos(tweet)
            tweet.hasSingleVideo() -> setupVideo(tweet)
        }

        when {
            tweet.quotedStatus -> setupQuotedStatus(tweet)
            tweet.hasLinks() -> setupUrlPreview(tweet)
        }
    }

    private fun setupPhoto(tweet: Tweet) {
        if (inflatedMediaView == null) {
            mediaViewStub.layoutResource = R.layout.stub_photo
            inflatedMediaView = mediaViewStub.inflate()
        }

        (inflatedMediaView as ImageView).loadUrl(tweet.getImageUrl())
        inflatedMediaView?.setOnClickListener { listener.showImage(tweet.getImageUrl()) }
    }

    private fun setupPhotos(tweet: Tweet) {
        if (inflatedMediaView == null) {
            mediaViewStub.layoutResource = R.layout.stub_photos
            inflatedMediaView = mediaViewStub.inflate()
        }

        val recyclerView = inflatedMediaView as RecyclerView
        recyclerView.setHasFixedSize(true)
        recyclerView.addItemDecoration(SpaceLeftItemDecoration(5))
        recyclerView.adapter = ImagesAdapter(tweet.mediaEntities, listener)
        recyclerView.layoutManager = LinearLayoutManager(container.context,
                LinearLayoutManager.HORIZONTAL, false)
    }

    private fun setupVideo(tweet: Tweet) {
        if (inflatedMediaView == null) {
            mediaViewStub.layoutResource = R.layout.video_cover
            inflatedMediaView = mediaViewStub.inflate()
        }

        (inflatedMediaView?.findViewById(R.id.tweetVideoImageView) as ImageView)
                .loadUrlCenterCrop(tweet.getVideoCoverUrl())

        inflatedMediaView?.findViewById(R.id.playVideoImageButton)?.setOnClickListener {
            val pair = tweet.getVideoUrlType()
            listener.showVideo(pair.first, pair.second)
        }
    }

    private fun setupQuotedStatus(tweet: Tweet) {
        if (inflatedQuotedView == null) {
            quotedStatusViewStub.layoutResource = R.layout.quoted_tweet
            inflatedQuotedView = quotedStatusViewStub.inflate()
        }

        val quotedStatus = tweet.getQuotedTweet()

        val photoImageView = inflatedQuotedView?.findViewById(R.id.photoImageView) as ImageView
        (inflatedQuotedView?.findViewById(R.id.quotedUserNameTextView) as TextView).text =
                quotedStatus.user.name

        // TODO other medias
        if (quotedStatus.hasSingleImage()) {
            photoImageView.visible()
            photoImageView.loadUrl(quotedStatus.getImageUrl())

            (inflatedQuotedView?.findViewById(R.id.quotedStatusTextView) as TextView).text =
                    quotedStatus.getTextWithoutMediaURLs()
        } else {
            (inflatedQuotedView as View).visible(false)
            (inflatedQuotedView?.findViewById(R.id.quotedStatusTextView) as TextView).text =
                    quotedStatus.text
        }

        inflatedQuotedView?.setOnClickListener { listener.openTweet(quotedStatus) }
    }

    private fun setupUrlPreview(tweet: Tweet) {
        if (inflatedUrlPreviewView == null) {
            urlPreviewViewStub.layoutResource = R.layout.url_preview
            inflatedUrlPreviewView = urlPreviewViewStub.inflate()
        }

        val previewImageView = inflatedUrlPreviewView
                ?.findViewById(R.id.urlPreviewImageView) as ImageView
        val descriptionTextView = inflatedUrlPreviewView
                ?.findViewById(R.id.urlDescriptionTextView) as TextView
        val titleTextView = inflatedUrlPreviewView?.findViewById(R.id.urlTitleTextView) as TextView
        val loadingProgressBar = inflatedUrlPreviewView?.findViewById(R.id.loadingProgressBar)

        UrlInfo.generatePreview(tweet.getLink())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    run {
                        tweet.metaData =
                                MetaData(if (it.images.isNotEmpty()) it.images[0].source
                                else null, it.title, it.description, tweet.getLink())

                        loadingProgressBar?.visible(false)
                        if (it.images.isNotEmpty())
                            previewImageView.loadUrl(it.images[0].source)
                        else
                            previewImageView.visible(false)
                        titleTextView.text = it.title
                        descriptionTextView.text = it.description
                        val url = it.url
                        inflatedUrlPreviewView?.setOnClickListener {
                            openUrl(container.context as Activity, url)
                        }
                    }
                }, { e -> Timber.e(e, "Error loading url preview") })
    }

}