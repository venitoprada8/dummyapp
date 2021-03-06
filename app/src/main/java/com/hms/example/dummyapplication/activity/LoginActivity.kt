package com.hms.example.dummyapplication.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginResult
import com.facebook.login.widget.LoginButton
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.SignInButton
import com.hms.example.dummyapplication.R
import com.hms.example.dummyapplication.utils.AccountBindingAsync
import com.hms.example.dummyapplication.utils.DemoConstants
import com.hms.example.dummyapplication.utils.LoadingDialog
import com.huawei.agconnect.auth.*
import com.huawei.hms.common.ApiException
import com.huawei.hms.support.hwid.HuaweiIdAuthManager
import com.huawei.hms.support.hwid.request.HuaweiIdAuthParams
import com.huawei.hms.support.hwid.request.HuaweiIdAuthParamsHelper
import net.openid.appauth.*

class LoginActivity : AppCompatActivity(), View.OnClickListener,
    AccountBindingAsync.OnAccountBindListener {
    private val GOOGLE_SIGN_IN: Int = 1001
    val HWID_SIGN_IN = 1003
    //var hasGooglePlayServices = false
    val TAG = "LoginActivity"
    lateinit var mCallbackManager: CallbackManager
    lateinit var loadingDialog: AlertDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        val hwBtn: Button = findViewById(R.id.hw)
        val fbBtn: LoginButton = findViewById(R.id.fb)
        fbBtn.setPermissions("email", "public_profile")
        hwBtn.setOnClickListener(this)
        findViewById<Button>(R.id.anon).setOnClickListener(this)
        setupGoogleSignIn()
        loadingDialog= LoadingDialog.createDialog(this)
        val appLinkIntent = getIntent()
        //val appLinkAction = appLinkIntent.getAction()
        val appLinkData = appLinkIntent.getData()
        if (appLinkData != null) {
            val openId = appLinkData.getQueryParameter("openId")
            if (openId != null && openId != "") {
                if (AGConnectAuth.getInstance().currentUser != null) {
                    val user: AGConnectUser = AGConnectAuth.getInstance().currentUser
                    Log.e("User", "${user.displayName}\t${user.email}\t${user.uid}\t${user.phone}")
                    displayDialog(user.displayName, user.uid, openId)
                }

            }
        }
        mCallbackManager = CallbackManager.Factory.create()
        fbBtn.registerCallback(mCallbackManager, object : FacebookCallback<LoginResult> {
            override fun onSuccess(loginResult: LoginResult) {
                Log.d(TAG, "facebook:onSuccess:$loginResult")
                val accessToken: String = loginResult.accessToken.token
                val credential = FacebookAuthProvider.credentialWithToken(accessToken)
                AGConnectAuth.getInstance().signIn(credential).addOnSuccessListener {
                    // onSuccess
                    val user = it.user
                    startNavDrawer(user)
                }.addOnFailureListener {
                    // onFail
                    Log.e(TAG, it.toString())
                }
            }

            override fun onCancel() {
                Log.d(TAG, "facebook:onCancel")
                // ...
            }

            override fun onError(error: FacebookException) {
                Log.d(TAG, "facebook:onError", error)
                // ...
            }
        })
    }

    private fun setupGoogleSignIn() {
        /*val status = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this)
        if (status == ConnectionResult.SUCCESS)
            hasGooglePlayServices = true*/
        val googleButton = findViewById<SignInButton>(R.id.google_sign_in_button)
        googleButton.setOnClickListener(this)
    }

    private fun startNavDrawer(user: AGConnectUser) {
        if(loadingDialog.isShowing) loadingDialog.dismiss()
        val intent = Intent(this, NavDrawer::class.java)
        if (user.displayName != null)
            intent.putExtra(DemoConstants.DISPLAY_NAME, user.displayName)
        intent.putExtra(DemoConstants.USER_ID, user.uid)
        startActivity(intent)
        finish()
    }


    private fun displayDialog(displayName: String, uid: String, openId: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Account binding")
        builder.setMessage("Continue as $displayName?")
        builder.setCancelable(false)
        builder.setPositiveButton(R.string.ok) { dialog, _ ->
            postData(uid, openId)
            dialog.dismiss()
            loadingDialog.show()
        }
        builder.setNegativeButton(R.string.cancel) { dialog, _ ->
            dialog.dismiss()
            finish()
        }

        builder.create().show()

    }

    private fun postData(uid: String, openId: String) {
        val task = AccountBindingAsync(
            uid,
            openId,
            this
        )
        task.execute()

    }

    override fun onAccountBind(result: Int) {
        if(loadingDialog.isShowing) loadingDialog.dismiss()
        Toast.makeText(this, "Account Bind Code:" + result, Toast.LENGTH_SHORT).show()
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)

    }

    override fun onClick(v: View?) {
        loadingDialog.show()
        when (v?.id) {
            R.id.hw -> {
                val mAuthParam =
                    HuaweiIdAuthParamsHelper(HuaweiIdAuthParams.DEFAULT_AUTH_REQUEST_PARAM)
                        .setIdToken()
                        .setAccessToken()
                        .createParams()


                val mAuthManager = HuaweiIdAuthManager.getService(this, mAuthParam)
                startActivityForResult(
                    mAuthManager.signInIntent,
                    HWID_SIGN_IN
                )
            }

            R.id.google_sign_in_button -> {
                    val serviceConfig = AuthorizationServiceConfiguration(
                        Uri.parse("https://accounts.google.com/o/oauth2/auth"), // authorization endpoint
                        Uri.parse("https://oauth2.googleapis.com/token")
                    ) // token endpoint
                    val clientId=getString(R.string.google_client_id)
                    val authRequestBuilder = AuthorizationRequest.Builder(
                        serviceConfig,  // the authorization service configuration
                        clientId,  // the client ID, typically pre-registered and static
                        ResponseTypeValues.CODE,  //
                        Uri.parse("$packageName:/oauth2redirect")
                    ) // the redirect URI to which the auth response is sent
                    authRequestBuilder.setScope("openid email profile")
                    val authRequest = authRequestBuilder.build()
                    val authService = AuthorizationService(this)
                    val authIntent = authService.getAuthorizationRequestIntent(authRequest)
                    startActivityForResult(authIntent, GOOGLE_SIGN_IN)
                    authService.dispose()
                //}
            }

            R.id.anon -> {
                AGConnectAuth.getInstance().signInAnonymously().addOnSuccessListener {
                    // onSuccess
                    val user = it.user
                    startNavDrawer(user)
                }.addOnFailureListener {
                    // onFail

                }
            }
        }
    }



    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == HWID_SIGN_IN) {
            //Huawei login success
            val authHuaweiIdTask = HuaweiIdAuthManager.parseAuthResultFromIntent(data)
            if (authHuaweiIdTask.isSuccessful) {
                val huaweiAccount = authHuaweiIdTask.result//
                val accessToken = huaweiAccount.accessToken
                val credential = HwIdAuthProvider.credentialWithToken(accessToken)
                AGConnectAuth.getInstance().signIn(credential).addOnSuccessListener {
                    // onSuccess
                    val user = it.user
                    startNavDrawer(user)
                }.addOnFailureListener {
                    // onFail
                    Log.e(TAG, it.toString())
                }
            } else {
                Log.i(
                    TAG,
                    "signIn get code failed: " + (authHuaweiIdTask.exception as ApiException).statusCode
                )
            }
        }
        else if(requestCode == GOOGLE_SIGN_IN){
            if (data != null) {
                val response = AuthorizationResponse.fromIntent(data)
                val ex = AuthorizationException.fromIntent(data)
                val authState = AuthState(response, ex)
                if (response != null) {
                    val service = AuthorizationService(this)
                    service.performTokenRequest(
                        response.createTokenExchangeRequest()
                    ) { tokenResponse, exception ->
                        service.dispose()
                        if (exception != null) {
                            Log.e(TAG, "Token Exchange failed", exception)
                        } else {
                            if (tokenResponse != null) {
                                authState.update(tokenResponse, exception)
                                Log.e(
                                    TAG,
                                    "Token Response [ Access Token: ${tokenResponse.accessToken}, ID Token: ${tokenResponse.idToken}"
                                )

                                val credential = GoogleAuthProvider.credentialWithToken(tokenResponse.idToken)
                                AGConnectAuth.getInstance().signIn(credential).addOnSuccessListener {
                                    // onSuccess
                                    val user = it.user
                                    startNavDrawer(user)
                                }.addOnFailureListener {
                                    Log.e(TAG,it.toString())
                                }
                            }
                        }
                    }
                }
            }
        }
        else {
            mCallbackManager.onActivityResult(requestCode, resultCode, data)//For facebook
        }
    }
}
