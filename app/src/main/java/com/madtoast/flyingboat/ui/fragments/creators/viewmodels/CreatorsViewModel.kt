package com.madtoast.flyingboat.ui.fragments.creators.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.madtoast.flyingboat.R
import com.madtoast.flyingboat.api.floatplane.ErrorHandler
import com.madtoast.flyingboat.api.floatplane.model.creator.Creator
import com.madtoast.flyingboat.data.FloatplaneRepository
import com.madtoast.flyingboat.data.Result
import com.madtoast.flyingboat.ui.activities.ui.login.UiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.threeten.bp.Instant

class CreatorsViewModel(private val floatplaneRepository: FloatplaneRepository) : ViewModel() {
    private val _creatorsResult = MutableLiveData<UiResult<Array<Creator>>>()
    val creatorsResult: LiveData<UiResult<Array<Creator>>> = _creatorsResult

    private var creatorsLoaded = false

    private val _errorHandler = ErrorHandler()
    private var hasInitialized = false

    fun init() {
        if (!hasInitialized) {
            floatplaneRepository.init()
            hasInitialized = true
        }
    }

    suspend fun listPlatformCreators(
        search: String,
        forceLoad: Boolean = false,
        sortedBy: Comparator<Creator> = Creator.SubscribedComparator()
    ) {
        if (!creatorsLoaded || forceLoad) {
            try {
                val creators =
                    floatplaneRepository.handleResponse(
                        floatplaneRepository.creatorV3().list(search)
                    )

                val subscriptions =
                    floatplaneRepository.handleResponse(
                        floatplaneRepository.userV3().subscriptions()
                    )

                if (creators is Result.Success && subscriptions is Result.Success) {
                    subscriptions.data?.apply {
                        val subscribedCreators = HashSet<String>()
                        val now = Instant.now()

                        for (subscription in this) {
                            val subscriptionStart = Instant.parse(subscription.startDate)
                            val subscriptionEnd = Instant.parse(subscription.endDate)

                            if (subscriptionStart.isBefore(now) && subscriptionEnd.isAfter(now)) {
                                subscribedCreators.add(subscription.creator!!)
                            }
                        }

                        //Set the creators that are subscribed
                        creators.data?.apply {
                            for (creator in this) {
                                if (subscribedCreators.isEmpty()) {
                                    break //Avoid iterating through the rest of the creators, we're done.
                                }

                                //Check for subscriptions and remove from list
                                if (subscribedCreators.contains(creator.id)) {
                                    creator.userSubscribed = true
                                    subscribedCreators.remove(creator.id)
                                }
                            }
                        }
                    }

                    creators.data?.sortWith(sortedBy)
                }

                CoroutineScope(Dispatchers.Main).launch {
                    if (creators is Result.Success) {
                        _creatorsResult.value =
                            UiResult(success = creators.data)
                        creatorsLoaded = true
                    } else {
                        _creatorsResult.value = UiResult(
                            error = _errorHandler.handleResponseError(
                                creators,
                                R.string.bad_request,
                                R.string.bad_token
                            )
                        )
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace() //Print the stack trace
                CoroutineScope(Dispatchers.Main).launch {
                    _creatorsResult.value = UiResult(
                        error = R.string.network_error
                    )
                }
            }
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            _creatorsResult.value = _creatorsResult.value
            creatorsLoaded = true
        }
    }
}