package com.mob.lee.fastair.viewmodel

import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import com.mob.lee.fastair.PermissionFragment
import com.mob.lee.fastair.model.DataLoad
import com.mob.lee.fastair.model.DataWrap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

open class AppViewModel : ViewModel() {
    val stateLiveData = DataLoad<Any>()

    /**
     * 执行耗时任务，不会阻塞UI
     */
    /*fun <D> async(action: suspend () -> DataWrap<D>): LiveData<D> {
        val liveData = MutableLiveData<D>()
        stateLiveData.value = StatusLoading()
        viewModelScope.launch(Dispatchers.Main) {
            val result = action()
            stateLiveData.value = if (result.isSuccess()) {
                liveData.value = result.data
                StatusSuccess()
            } else {
                StatusError(result.msg)
            }
        }
        return liveData
    }*/

    fun <D> async(liveData: MutableLiveData<D>? = null, action: suspend DataLoad<D>.() -> Unit): LiveData<D> {
        val targetLiveData = liveData.apply { this?.value = null } ?: MutableLiveData<D>()

        val dataLoad = DataLoad<D>()

        val observer = Observer<Pair<Int, D?>> {
            stateLiveData.value = it
            if (it.first == DataLoad.NEXT) {
                targetLiveData.value = it?.second
            }
        }

        dataLoad.observeForever(observer)

        viewModelScope.launch(Dispatchers.Main) {
            try {
                action(dataLoad)

                when (dataLoad.code) {
                    DataLoad.LOADING -> dataLoad.empty()
                    DataLoad.NEXT -> dataLoad.complete()
                }
                dataLoad.removeObserver(observer)
            } catch (e: Exception) {
                dataLoad.error(e.message)
            } finally {
                stateLiveData.value = null
            }
        }
        return targetLiveData
    }

    fun <D> asyncWithWrap(liveData: MutableLiveData<DataWrap<D>>? = null, action: suspend () -> DataWrap<D>): LiveData<DataWrap<D>> = async(liveData) {
        val data = action()
        next(data)
    }

    fun withPermission(fragment: Fragment, vararg permissions: String, action: (Int: Int, hasPermission: Boolean) -> Unit) {
        val target = ArrayList<String>()
        permissions.forEachIndexed { index, s ->
            if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(fragment.requireContext(), s)) {
                target.add(s)
            } else {
                action(index, true)
            }
        }
        if (target.isNotEmpty()) {
            val viewmodel = ViewModelProviders.of(fragment.requireActivity()).get(PermissionViewModel::class.java)
            val fragmentManager = fragment.activity?.supportFragmentManager
            val f = PermissionFragment.request(target)

            viewmodel.permissionLiveData.observe(f, Observer {
                it ?: return@Observer
                viewmodel.permissionLiveData.value = null
                fragmentManager?.beginTransaction()?.remove(f)?.commit()
                it.forEachIndexed { index, state ->
                    action(index, PackageManager.PERMISSION_GRANTED == state)
                }
            })

            fragmentManager?.beginTransaction()?.add(android.R.id.content, f, "permission")
                    ?.show(f)
                    ?.commit()
        }
    }
}