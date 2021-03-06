package org.grameenfoundation.cch.supervisor.activity;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import org.grameenfoundation.cch.supervisor.R;
import org.grameenfoundation.cch.supervisor.Supervisor;
import org.grameenfoundation.cch.supervisor.model.Payload;
import org.grameenfoundation.cch.supervisor.model.User;
import org.grameenfoundation.cch.supervisor.task.LoginTask;
import org.grameenfoundation.cch.supervisor.task.SubmitListener;
import org.grameenfoundation.cch.supervisor.util.ConnectionUtils;

import java.util.ArrayList;

/**
 * Activity which displays a login screen to the user, offering registration as
 * well.
 */
public class LoginActivity extends Activity implements SubmitListener {

	public static final String TAG = LoginActivity.class.getSimpleName();
	public static final String EXTRA_USERNAME = "username";

	LoginTask mAuthTask = null;
	
	// Values for email and password at the time of the login attempt.
	private String mUsername;
	private String mPassword;

	// UI references.
	private EditText mUsernameView;
	private EditText mPasswordView;
	private View mLoginFormView;
	private View mLoginStatusView;
	private TextView mLoginStatusMessageView;
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_login);
		
		// Set up the login form.
		mUsername = getIntent().getStringExtra(EXTRA_USERNAME);
		mUsernameView = (EditText) findViewById(R.id.username);
		mUsernameView.setText(mUsername);

		mPasswordView = (EditText) findViewById(R.id.password);
		mPasswordView
				.setOnEditorActionListener(new TextView.OnEditorActionListener() {
					@Override
					public boolean onEditorAction(TextView textView, int id,
							KeyEvent keyEvent) {
						if (id == R.id.login || id == EditorInfo.IME_NULL) {
							attemptLogin();
							return true;
						}
						return false;
					}
				});

		mLoginFormView = findViewById(R.id.login_form);
		mLoginStatusView = findViewById(R.id.login_status);
		mLoginStatusMessageView = (TextView) findViewById(R.id.login_status_message);

		findViewById(R.id.sign_in_button).setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						attemptLogin();
					}
				});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.login, menu);
		return true;
	}
	
	public void setUserPreferences(User u) {
		// set params
		Editor editor = Supervisor.getPreferences().edit();
    	editor.putString(getString(R.string.prefs_username), mUsernameView.getText().toString());
    	editor.putString(getString(R.string.prefs_api_key), u.getApi_key());
    	editor.putString(getString(R.string.prefs_display_name), u.getDisplayName());
    	editor.commit();
	}
	

	/**
	 * Attempts to sign in or register the account specified by the login form.
	 * If there are form errors (invalid email, missing fields, etc.), the
	 * errors are presented and no actual login attempt is made.
	 */
	public void attemptLogin() {
		if (mAuthTask != null) {
			return;
		}

		// Reset errors.
		mUsernameView.setError(null);
		mPasswordView.setError(null);

		// Store values at the time of the login attempt.
		mUsername = mUsernameView.getText().toString();
		mPassword = mPasswordView.getText().toString();

		boolean cancel = false;
		View focusView = null;

		// Check for a valid password.
		if (TextUtils.isEmpty(mPassword)) {
			mPasswordView.setError(getString(R.string.error_field_required));
			focusView = mPasswordView;
			cancel = true;
		} else if (mPassword.length() < 2) {
			mPasswordView.setError(getString(R.string.error_invalid_password));
			focusView = mPasswordView;
			cancel = true;
		}

		// Check for a valid username address.
		if (TextUtils.isEmpty(mUsername)) {
			mUsernameView.setError(getString(R.string.error_field_required));
			focusView = mUsernameView;
			cancel = true;
		} else if (mUsername.length() < 3) {
			mUsernameView.setError(getString(R.string.error_invalid_username));
			focusView = mUsernameView;
			cancel = true;
		}

		if (cancel) {
			// There was an error; don't attempt login and focus the first
			// form field with an error.
			focusView.requestFocus();
		} else {
			// Show a progress spinner, and kick off a background task to
			// perform the user login attempt.
			mLoginStatusMessageView.setText(R.string.login_progress_signing_in);
			showProgress(true);
			
			User u = new User();
	    	u.setUsername(mUsername);
	    	u.setPassword(mPassword);
	    	
	    	u = Supervisor.Db.checkUserExists(u);
	    	
			if (u != null){
				if (u.isPasswordRight()) {
					setUserPreferences(u);
					startActivity(new Intent(this, StartupActivity.class));
					finish();
				} else {
					showProgress(false);
					mPasswordView.setError(getString(R.string.error_incorrect_password));
					focusView = mPasswordView;
					focusView.requestFocus();
					return;
				}
			} else {
				if (ConnectionUtils.isNetworkConnected(getApplicationContext())) {
				 	ArrayList<Object> users = new ArrayList<Object>();
			    	User un = new User();
			    	un.setUsername(mUsername);
			    	un.setPassword(mPassword);
				 	users.add(un);
		        	Payload p = new Payload(users);
		    		mAuthTask = new LoginTask(this);
		    		mAuthTask.setListener(this);
		    		mAuthTask.execute(p);
				} else {
					mPasswordView.setError(getString(R.string.error_nousernoconnection));
					focusView = mPasswordView;
					focusView.requestFocus();
	    			return;
	    		}
	    	}	
		}
	}
	
	public void submitComplete(Payload response) {
		showProgress(false);
			
		if(response.isResult()){
			// set preferences and add user to db
			User u = (User) response.getResponseData().get(0);
			setUserPreferences(u);
	    	Supervisor.Db.addUser(u);
	    	
			// return to main activity
	    	startActivity(new Intent(this, StartupActivity.class));
			finish();
		} else {
			mAuthTask = null;
			mPasswordView.setError(response.getResultResponse());
			mPasswordView.requestFocus();
		}
	}
	

	/**
	 * Shows the progress UI and hides the login form.
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
	private void showProgress(final boolean show) {
		// On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
		// for very easy animations. If available, use these APIs to fade-in
		// the progress spinner.
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
			int shortAnimTime = getResources().getInteger(
					android.R.integer.config_shortAnimTime);

			mLoginStatusView.setVisibility(View.VISIBLE);
			mLoginStatusView.animate().setDuration(shortAnimTime)
					.alpha(show ? 1 : 0)
					.setListener(new AnimatorListenerAdapter() {
						@Override
						public void onAnimationEnd(Animator animation) {
							mLoginStatusView.setVisibility(show ? View.VISIBLE
									: View.GONE);
						}
					});

			mLoginFormView.setVisibility(View.VISIBLE);
			mLoginFormView.animate().setDuration(shortAnimTime)
					.alpha(show ? 0 : 1)
					.setListener(new AnimatorListenerAdapter() {
						@Override
						public void onAnimationEnd(Animator animation) {
							mLoginFormView.setVisibility(show ? View.GONE
									: View.VISIBLE);
						}
					});
		} else {
			// The ViewPropertyAnimator APIs are not available, so simply show
			// and hide the relevant UI components.
			mLoginStatusView.setVisibility(show ? View.VISIBLE : View.GONE);
			mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
		}
	}
}
