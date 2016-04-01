package org.wordpress.android.networking.gravatar;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.converter.simplexml.SimpleXmlConverterFactory;

public class ServiceGenerator {

    private static HttpLoggingInterceptor logging = new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor
            .Level.BODY);

    private static OkHttpClient.Builder httpClient = new OkHttpClient.Builder().addInterceptor(logging);

    public static <S> S createService(Class<S> serviceClass, String baseUrl, final String token, boolean withXML) {
        Retrofit.Builder builder = new Retrofit.Builder()
                .baseUrl(baseUrl);

        if (withXML) {
            builder.addConverterFactory(SimpleXmlConverterFactory.create());
        } else {
            builder.addConverterFactory(GsonConverterFactory.create());
        }

        if (token != null) {
            httpClient.addInterceptor(new Interceptor() {
                @Override
                public Response intercept(Interceptor.Chain chain) throws IOException {
                    Request original = chain.request();

                    Request.Builder requestBuilder = original.newBuilder()
                            .header("Accept", "application/json")
                            .header("Authorization", "Bearer " + token)
                            .method(original.method(), original.body());

                    Request request = requestBuilder.build();
                    return chain.proceed(request);
                }
            });
        }

        OkHttpClient client = httpClient.build();
        Retrofit retrofit = builder.client(client).build();
        return retrofit.create(serviceClass);
    }
}