package me.longng.finnish_learning_mobile.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.longng.finnish_learning_mobile.data.api.service.AuthApi
import me.longng.finnish_learning_mobile.data.api.service.CardApi
import me.longng.finnish_learning_mobile.data.api.service.EvaluationApi
import me.longng.finnish_learning_mobile.data.api.service.ProgressApi
import me.longng.finnish_learning_mobile.data.api.service.QuizApi
import me.longng.finnish_learning_mobile.data.api.service.TopicApi
import retrofit2.Retrofit
import retrofit2.create
import javax.inject.Singleton

/**
 * Turns the single [Retrofit] instance (from [NetworkModule]) into the six typed
 * API interfaces. `Retrofit.create` returns a dynamic proxy that implements the
 * interface, so each of these is cheap; `@Singleton` just avoids re-creating the
 * proxy on every injection.
 */
@Module
@InstallIn(SingletonComponent::class)
object ApiModule {

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create()

    @Provides
    @Singleton
    fun provideTopicApi(retrofit: Retrofit): TopicApi = retrofit.create()

    @Provides
    @Singleton
    fun provideCardApi(retrofit: Retrofit): CardApi = retrofit.create()

    @Provides
    @Singleton
    fun provideQuizApi(retrofit: Retrofit): QuizApi = retrofit.create()

    @Provides
    @Singleton
    fun provideProgressApi(retrofit: Retrofit): ProgressApi = retrofit.create()

    @Provides
    @Singleton
    fun provideEvaluationApi(retrofit: Retrofit): EvaluationApi = retrofit.create()
}