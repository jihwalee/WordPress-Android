package org.xmlrpc.android.pojo;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface XMLRPCClientRetrofit {
    @POST("xmlrpc.php")
    Call<MethodResponse> call(@Body MethodCall methodCall);
}