package me.longng.finnish_learning_mobile.di

import android.content.Context
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.squareup.moshi.Moshi
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import me.longng.finnish_learning_mobile.data.auth.TokenStore
import me.longng.finnish_learning_mobile.data.api.AuthInterceptor
import me.longng.finnish_learning_mobile.data.api.MoshiProvider
import me.longng.finnish_learning_mobile.data.api.NetworkProvider
import me.longng.finnish_learning_mobile.util.EncryptedTokenStore
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * Provides the process-wide networking + auth singletons:
 * [Moshi], the [TokenStore], the [AuthInterceptor], the [OkHttpClient], and
 * [Retrofit]. Everything here is `@Singleton` — one instance per process.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = MoshiProvider.create()

    /**
     * Binds the [TokenStore] interface to its encrypted implementation.
     *
     * The `@ApplicationContext` qualifier hands us the long-lived application `Context`,
     * never an Activity, which would leak (see [EncryptedTokenStore]'s KDoc).
     */
    @Provides
    @Singleton
    fun provideTokenStore(@ApplicationContext context: Context): TokenStore =
        EncryptedTokenStore(context)

    @Provides
    @Singleton
    fun provideAuthInterceptor(tokenStore: TokenStore): AuthInterceptor =
        AuthInterceptor(tokenStore)

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient =
        NetworkProvider.okHttp(authInterceptor)

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        NetworkProvider.retrofit(okHttpClient, moshi)
}