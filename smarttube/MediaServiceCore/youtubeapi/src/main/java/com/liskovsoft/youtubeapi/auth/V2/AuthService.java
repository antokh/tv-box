package com.liskovsoft.youtubeapi.auth.V2;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.youtubeapi.app.AppService;
import com.liskovsoft.googlecommon.common.models.auth.AccessToken;
import com.liskovsoft.googlecommon.common.models.auth.UserCode;
import com.liskovsoft.googlecommon.common.models.auth.info.AccountInt;
import com.liskovsoft.googlecommon.common.models.auth.info.AccountsList;
import com.liskovsoft.googlecommon.common.helpers.RetrofitHelper;
import retrofit2.Call;

import java.util.List;

public class AuthService {
    private static final String TAG = AuthService.class.getSimpleName();
    private static AuthService sInstance;
    private final AuthApi mAuthApi;
    private static final int MIN_POLL_INTERVAL_SEC = 5;
    private static final int SLOW_DOWN_STEP_SEC = 5;
    private static final int DEFAULT_CODE_LIFETIME_SEC = 30 * 60;
    private final AppService mAppService;

    private AuthService() {
        mAuthApi = RetrofitHelper.create(AuthApi.class);
        mAppService = AppService.instance();
    }

    public static AuthService instance() {
        if (sInstance == null) {
            sInstance = new AuthService();
        }

        return sInstance;
    }

    /**
     * Returns user code that user should apply on the page<br/>
     * <a href=https://youtube.com/activate>https://youtube.com/activate</a>
     * @return response with user code and device code
     */
    public UserCode getUserCode() {
        Call<UserCode> wrapper = mAuthApi.getUserCode(
                AuthApiHelper.getUserCodeQuery(mAppService.getClientId())
        );
        return RetrofitHelper.get(wrapper);
    }

    /**
     * Note, before calling this method user should apply the 'user code' on the page<br/>
     * <a href=https://youtube.com/activate>https://youtube.com/activate</a>
     * @param deviceCode the code contained inside the response of the method {@link #getUserCode()}
     * @return refresh token that should be stored inside the app registry for future use
     */
    public AccessToken getAccessToken(String deviceCode) {
        Call<AccessToken> wrapper = mAuthApi.getAccessToken(
                AuthApiHelper.getRefreshTokenQuery(
                        deviceCode,
                        mAppService.getClientId(),
                        mAppService.getClientSecret())
        );
        return RetrofitHelper.get(wrapper);
    }

    /**
     * Returns temporal access token that should be refreshed after some period of time
     * @param refreshToken token obtained from previous method
     * @return temporal access token
     */
    public AccessToken updateAccessToken(String refreshToken) {
        Call<AccessToken> wrapper = mAuthApi.updateAccessToken(
                AuthApiHelper.getAccessTokenQuery(refreshToken,
                        mAppService.getClientId(),
                        mAppService.getClientSecret())
        );
        return RetrofitHelper.getWithErrors(wrapper);
    }

    public AccessToken updateAccessTokenRaw(String rawJsonAuthData) {
        Call<AccessToken> wrapper = mAuthApi.updateAccessToken(rawJsonAuthData);
        return RetrofitHelper.get(wrapper);
    }

    public AccessToken getAccessTokenWait(String deviceCode) throws InterruptedException {
        return getAccessTokenWait(deviceCode, 0, 0);
    }

    /**
     * TV Box build: polls like the OAuth device flow requires (RFC 8628 3.5): never faster than the
     * interval Google returned (min 5 s), +5 s on every "slow_down", stop when the code expires or is
     * denied. The old loop polled every 3 s for a fixed 10 min, got "slow_down" and then failed with a
     * message full of debug data that ended up on the TV screen.
     *
     * @param intervalSec  polling interval from {@link UserCode#getInterval()} (0 = default)
     * @param expiresInSec code lifetime from {@link UserCode#getExpiresIn()} (0 = default)
     */
    public AccessToken getAccessTokenWait(String deviceCode, int intervalSec, int expiresInSec) throws InterruptedException {
        long intervalMs = Math.max(intervalSec, MIN_POLL_INTERVAL_SEC) * 1_000L;
        long lifetimeMs = (expiresInSec > 0 ? expiresInSec : DEFAULT_CODE_LIFETIME_SEC) * 1_000L;
        long deadline = System.currentTimeMillis() + lifetimeMs;
        AccessToken tokenResult = null;

        while (System.currentTimeMillis() + intervalMs < deadline) {
            Thread.sleep(intervalMs);

            tokenResult = getAccessToken(deviceCode);

            if (tokenResult != null && tokenResult.getRefreshToken() != null) {
                return tokenResult;
            }

            String error = tokenResult != null ? tokenResult.getError() : null;
            if ("slow_down".equals(error)) {
                intervalMs += SLOW_DOWN_STEP_SEC * 1_000L;
            } else if ("expired_token".equals(error) || "access_denied".equals(error) || "invalid_grant".equals(error)) {
                break;
            }
            // "authorization_pending" or a network hiccup (null): keep waiting.
        }

        String error = tokenResult != null ? tokenResult.getError() : null;
        Log.e(TAG, "Sign-in code not used in time: %s", error);
        throw new IllegalStateException("Sign-in code expired" + (error != null ? " (" + error + ")" : ""));
    }

    /**
     * NOTE: Requires the auth header to be set before the call
     */
    @Nullable
    public List<AccountInt> getAccounts() {
        Call<AccountsList> wrapper = mAuthApi.getAccountsList(AuthApiHelper.getAccountsListQuery());

        AccountsList accountsList = RetrofitHelper.get(wrapper);

        return accountsList != null ? accountsList.getAccounts() : null;
    }
}
