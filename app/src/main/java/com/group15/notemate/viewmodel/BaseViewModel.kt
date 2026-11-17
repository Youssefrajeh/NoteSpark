package com.group15.notemate.viewmodel

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.group15.notemate.R
import com.group15.notemate.model.MessageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * MVVM: ViewModel. Shared isLoading flag, one-time messages and error handling for the
 * ViewModels that run slow work (ML Kit, file access) in coroutines.
 */
abstract class BaseViewModel(application: Application) : AndroidViewModel(application) {

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _message = MutableLiveData<Event<String>>()
    val message: LiveData<Event<String>> = _message

    protected fun getString(@StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    protected fun showMessage(@StringRes id: Int) {
        _message.value = Event(getString(id))
    }

    /** Runs [block] in viewModelScope, shows the progress bar and turns errors into messages. */
    protected fun launchWork(block: suspend () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: MessageException) {
                showMessage(e.messageRes)
            } catch (e: Exception) {
                _message.value = Event(getString(R.string.error_generic, e.localizedMessage.orEmpty()))
            } finally {
                _isLoading.value = false
            }
        }
    }
}
