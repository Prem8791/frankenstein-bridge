package com.android.systemui.prodx

import com.android.systemui.CoreStartable
import dagger.Binds
import dagger.Module
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

@Module
interface ProdXModule {
    @Binds
    fun bindAuthAdapter(impl: ProdXDeviceEntryAuthAdapter): ProdXAuthAdapter

    @Binds
    @IntoMap
    @ClassKey(ProdXRuntimeStartable::class)
    fun bindRuntimeStartable(impl: ProdXRuntimeStartable): CoreStartable
}
