package com.fgmachines.mikrotikmanager.ui

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/** Separate ViewModels and saved handles for each authenticated workspace. */
internal class BusinessWorkspaceOwner(app:Application,restored:Bundle?):ViewModelStoreOwner,SavedStateRegistryOwner,HasDefaultViewModelProviderFactory {
    override val viewModelStore=ViewModelStore()
    private val registry=LifecycleRegistry(this)
    override val lifecycle:Lifecycle get()=registry
    private val controller=SavedStateRegistryController.create(this)
    override val savedStateRegistry:SavedStateRegistry get()=controller.savedStateRegistry
    override val defaultViewModelProviderFactory:ViewModelProvider.Factory=SavedStateViewModelFactory(app,this)
    override val defaultViewModelCreationExtras:CreationExtras=MutableCreationExtras().apply {
        set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY,app)
        set(SAVED_STATE_REGISTRY_OWNER_KEY,this@BusinessWorkspaceOwner)
        set(VIEW_MODEL_STORE_OWNER_KEY,this@BusinessWorkspaceOwner)
    }
    init {controller.performAttach();enableSavedStateHandles();controller.performRestore(restored);registry.currentState=Lifecycle.State.CREATED}
    fun state(state:Lifecycle.State){if(registry.currentState!=Lifecycle.State.DESTROYED)registry.currentState=state}
    fun save()=Bundle().also{controller.performSave(it)}
    fun close(){registry.currentState=Lifecycle.State.DESTROYED;viewModelStore.clear()}
}
