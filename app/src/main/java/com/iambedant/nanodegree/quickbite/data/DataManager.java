package com.iambedant.nanodegree.quickbite.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.v4.content.Loader;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.iambedant.nanodegree.quickbite.data.local.PreferencesHelper;
import com.iambedant.nanodegree.quickbite.data.local.persistent.DataContract;
import com.iambedant.nanodegree.quickbite.data.local.persistent.ProviderHelper;
import com.iambedant.nanodegree.quickbite.data.model.Cuisines.Cuisines;
import com.iambedant.nanodegree.quickbite.data.model.Favourite;
import com.iambedant.nanodegree.quickbite.data.model.Reviews.Reviews;
import com.iambedant.nanodegree.quickbite.data.model.SearchResult.Restaurant_;
import com.iambedant.nanodegree.quickbite.data.model.SearchResult.SearchResult;
import com.iambedant.nanodegree.quickbite.data.model.User;
import com.iambedant.nanodegree.quickbite.data.remote.QuickBiteAPIClient;
import com.iambedant.nanodegree.quickbite.events.EventLoginSuccessfull;
import com.iambedant.nanodegree.quickbite.events.EventLogout;
import com.iambedant.nanodegree.quickbite.events.EventName;
import com.iambedant.nanodegree.quickbite.events.EventNameSuccessfull;
import com.iambedant.nanodegree.quickbite.events.EventPasswordUpdate;
import com.iambedant.nanodegree.quickbite.events.RestaurantAddOrDeleteSuccessful;
import com.iambedant.nanodegree.quickbite.util.Constants;
import com.iambedant.nanodegree.quickbite.util.EventPosterHelper;
import com.iambedant.nanodegree.quickbite.util.Logger;

import org.greenrobot.eventbus.EventBus;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;
import rx.functions.Action0;

@Singleton
public class DataManager {

    private final String TAG = DataManager.class.getSimpleName();
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private final PreferencesHelper mPreferencesHelper;
    private final EventPosterHelper mEventPoster;
    private final QuickBiteAPIClient mQuickBiteApiClient;
    private final ProviderHelper mProviderHelper;


    @Inject
    public DataManager(PreferencesHelper preferencesHelper,
                       EventPosterHelper eventPosterHelper,
                       QuickBiteAPIClient quickBiteAPIClient,
                       ProviderHelper providerHelper,
                       FirebaseAuth auth,
                       DatabaseReference databaseReference) {
        mPreferencesHelper = preferencesHelper;
        mEventPoster = eventPosterHelper;
        mQuickBiteApiClient = quickBiteAPIClient;
        mProviderHelper = providerHelper;
        mAuth = auth;
        mDatabase = databaseReference;

    }


    public PreferencesHelper getPreferencesHelper() {
        return mPreferencesHelper;
    }

    /// Helper method to post events from doOnCompleted.
    private Action0 postEventAction(final Object event) {
        return new Action0() {
            @Override
            public void call() {
                mEventPoster.postEventSafely(event);
            }
        };
    }


    public Observable<SearchResult> getSearchData(Map<String, String> queryParams) {
        return mQuickBiteApiClient.getSearchResult(queryParams);
    }

    public Observable<Cuisines> getNearbyCuisines(Map<String, String> queryParams) {
        return mQuickBiteApiClient.getCuisines(queryParams);
    }

    public void saveCusinesToDb(Vector<ContentValues> cVVector) {

        mProviderHelper.deleteAllCuisines();
        Logger.d(TAG, "Old Cuisines Deleted");
        mProviderHelper.saveAllCuisines(cVVector);
        Logger.d(TAG, "New Cuisines Stored");
    }

    public void saveFavouriteRestaurant(Restaurant_ mRestaurant) {
        if (mProviderHelper.isRestaurantPresent(mRestaurant.getId())) {
            Logger.d(TAG, "Already in Favourite");
        } else {
            ContentValues values = new ContentValues();
            values.put(DataContract.RestaurantEntry.COLUMN_RESTAURANT_ID, mRestaurant.getId());
            values.put(DataContract.RestaurantEntry.COLUMN_RESTAURANT_NAME, mRestaurant.getName());
            values.put(DataContract.RestaurantEntry.COLUMN_RESTAURANT_COVER_IMAGE, mRestaurant.getFeaturedImage());
            values.put(DataContract.RestaurantEntry.COLUMN_RESTAURANT_CUISINE, mRestaurant.getCuisines());
            values.put(DataContract.RestaurantEntry.COLUMN_RESTAURANT_LAT, mRestaurant.getLocation().getLatitude());
            values.put(DataContract.RestaurantEntry.COLUMN_RESTAURANT_LONG, mRestaurant.getLocation().getLongitude());
            values.put(DataContract.RestaurantEntry.COLUMN_RESTAURANT_ADDRESS, mRestaurant.getLocation().getAddress());
            values.put(DataContract.RestaurantEntry.COLUMN_RESTAURANT_RATINGE, mRestaurant.getUserRating().getAggregateRating());
            values.put(DataContract.RestaurantEntry.COLUMN_RESTAURANT_PRICE, mRestaurant.getAverageCostForTwo());
            mProviderHelper.saveSingleRestaurant(values);
        }
    }

    public void deleteFavouriteRestaurant(String id) {
        mProviderHelper.deleteSingleRestaurant(id);
    }

    public void saveCurrentLocation(Double lat, Double lon, String locality) {
        mPreferencesHelper.putString(Constants.LAST_KNOWN_LOCALITY, locality);
        mPreferencesHelper.putDouble(Constants.LAST_KNOWN_LAT, lat);
        mPreferencesHelper.putDouble(Constants.LAST_KNOWN_LON, lon);

    }

    public String getLastKnownLocation() {
        return mPreferencesHelper.getString(Constants.LAST_KNOWN_LOCALITY, "");
    }

    public Loader<Cursor> getFavouriteRestaurants() {
        return mProviderHelper.getFavouriteRestaurants();
    }

    public Observable<Reviews> getReviews(HashMap<String, String> params) {
        return mQuickBiteApiClient.getReviews(params);
    }

    public String getLon() {
        return mPreferencesHelper.getDouble(Constants.LAST_KNOWN_LON, 0.0) + "";
    }


    public String getLat() {
        return mPreferencesHelper.getDouble(Constants.LAST_KNOWN_LAT, 0.0) + "";
    }

    public boolean isRestaurantPresent(String id) {
        return mProviderHelper.isRestaurantPresent(id);
    }

    public Cursor getCuisines(String queryString) {
        Cursor mCursor = null;
        mCursor = mProviderHelper.getCuisines(queryString);
        return mCursor;
    }

    public void addFavourites(ContentValues values) {
        mProviderHelper.saveSingleRestaurant(values);

    }

    public FirebaseUser getCurrentUser() {
        return mAuth.getCurrentUser();
    }

    public void writeNewUser(String userId, String name, String email, final int TYPE) {
        User user = new User(name, email);
        mDatabase.child("users").child(userId).updateChildren(user.toMap());
        mDatabase.child("users").child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (TYPE == Constants.LOGIN) {
                    getFavouriteRestaurants(mAuth.getCurrentUser().getUid());
                } else {
                    EventBus.getDefault().post(new EventLoginSuccessfull(true, ""));
                }

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Logger.d(TAG, databaseError.getMessage());
            }
        });

    }

    public void getFavouriteRestaurants(String userId) {
        Logger.d(TAG, "getFavourite Restaurant Called");
        final Query mQuery = mDatabase.child("users").child(userId).child("favourites");


        mQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Logger.d(TAG, "ParsingHashMap");
                HashMap<String, HashMap<String, String>> mMap = (HashMap<String, HashMap<String, String>>) dataSnapshot.getValue();
                saveRestaurantsToLocalStorage(mMap);

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    public void saveRestaurantsToLocalStorage(HashMap<String, HashMap<String, String>> mMap) {
        if (mMap != null) {
            for (Map.Entry<String, HashMap<String, String>> entry : mMap.entrySet()) {

                HashMap<String, String> favourite = entry.getValue();
                AddFavourites(new Favourite(favourite.get("restaurantId"),
                        favourite.get("restaurantName"),
                        favourite.get("coverImage"),
                        favourite.get("cuisine"),
                        favourite.get("address"),
                        favourite.get("lat"),
                        favourite.get("lon"),
                        favourite.get("rating"),
                        Integer.parseInt(String.valueOf(favourite.get("price")))
                ));
            }
        }
        Logger.d(TAG, "Invoking event Bus");
        EventBus.getDefault().post(new EventLoginSuccessfull(true, ""));
    }

    public void AddFavourites(final Favourite mRestaurant) {
        Logger.d(TAG, "Adding Favourite");
        if (!isRestaurantPresent(mRestaurant.getRestaurantId())) {
            Logger.d(TAG, "Previously Not Present");
            ContentValues values = new ContentValues();

            values.put(DataContract.RestaurantEntry.COLUMN_RESTAURANT_ID, mRestaurant.getRestaurantId());
            values.put(DataContract.RestaurantEntry.COLUMN_RESTAURANT_NAME, mRestaurant.getRestaurantName());
            values.put(DataContract.RestaurantEntry.COLUMN_RESTAURANT_COVER_IMAGE, mRestaurant.getCoverImage());
            values.put(DataContract.RestaurantEntry.COLUMN_RESTAURANT_CUISINE, mRestaurant.getCuisine());
            values.put(DataContract.RestaurantEntry.COLUMN_RESTAURANT_LAT, mRestaurant.getLat());
            values.put(DataContract.RestaurantEntry.COLUMN_RESTAURANT_LONG, mRestaurant.getLon());
            values.put(DataContract.RestaurantEntry.COLUMN_RESTAURANT_ADDRESS, mRestaurant.getAddress());
            values.put(DataContract.RestaurantEntry.COLUMN_RESTAURANT_RATINGE, mRestaurant.getRating());
            values.put(DataContract.RestaurantEntry.COLUMN_RESTAURANT_PRICE, mRestaurant.getPrice());


            addFavourites(values);
        }


    }

    public void firebaseLogin(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        Log.d(TAG, "signIn:onComplete:" + task.isSuccessful());
                        if (task.isSuccessful()) {
                            getFavouriteRestaurants(task.getResult().getUser().getUid());
                        } else {
                            EventBus.getDefault().post(new EventLoginSuccessfull(false, task.getException().getMessage()));
                        }
                    }
                });
    }

    public void createFirebaseUser(final String email, final String password, final String name) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                    .setDisplayName(name).build();
                            mAuth.getCurrentUser().updateProfile(profileUpdates);
                            writeNewUser(task.getResult().getUser().getUid(), name, task.getResult().getUser().getEmail(), Constants.REGISTER);
                        } else {
                            Logger.d(TAG, "Failed");
                            EventBus.getDefault().post(new EventLoginSuccessfull(false, task.getException().getMessage()));
                        }
                    }
                });

    }

    public String deleteRestaurantFromFirebase(final String restaurantId) {
        mDatabase.child("users").child(mAuth.getCurrentUser().getUid()).child("favourites").child(restaurantId).removeValue().addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                deleteFavouriteRestaurant(restaurantId);
                EventBus.getDefault().post(new RestaurantAddOrDeleteSuccessful(true));
            }
        });
        return "hello";
    }

    public void writeNewPost(String userId, final Restaurant_ mRestaurant) {

        Favourite favourite = new Favourite(mRestaurant.getId(), mRestaurant.getName(), mRestaurant.getFeaturedImage(), mRestaurant.getCuisines(), mRestaurant.getLocation().getAddress(), mRestaurant.getLocation().getLatitude(), mRestaurant.getLocation().getLongitude(), mRestaurant.getUserRating().getAggregateRating(), mRestaurant.getAverageCostForTwo());
        Map<String, Object> postValues = favourite.toMap();
        Map<String, Object> childUpdates = new HashMap<>();
        childUpdates.put("/users/" + userId + "/favourites/" + mRestaurant.getId(), postValues);
        mDatabase.updateChildren(childUpdates).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                saveFavouriteRestaurant(mRestaurant);
                EventBus.getDefault().post(new RestaurantAddOrDeleteSuccessful(true));
            }
        });
    }

    public void getUserName() {

        String fullname = mAuth.getCurrentUser().getDisplayName();
        String firstName = "User";
        if (!fullname.trim().isEmpty()) {
            String[] splitNmae = fullname.split(" ");
            firstName = splitNmae[0];

        }
        EventBus.getDefault().post(new EventName(firstName));
    }

    public void logoutUser() {
        mPreferencesHelper.clear();
        mProviderHelper.clear();
        mAuth.signOut();
        EventBus.getDefault().post(new EventLogout(true));
    }

    public void updateName(String name) {
        UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                .setDisplayName(name).build();
        mAuth.getCurrentUser().updateProfile(profileUpdates).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                EventBus.getDefault().post(new EventNameSuccessfull(task.isSuccessful()));
            }
        });
    }

    public void updatePassword(String oldPassword, final String newPassword) {


        AuthCredential credential = EmailAuthProvider
                .getCredential(mAuth.getCurrentUser().getEmail(), oldPassword);
        mAuth.getCurrentUser().reauthenticate(credential)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            updateNewPassword(newPassword);
                        } else {
                            EventBus.getDefault().post(new EventPasswordUpdate(false, task.getException().getMessage()));
                        }
                    }
                });


    }

    public int getProviderType() {
        int type = 0;
        String providerId = "";
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            for (UserInfo profile : user.getProviderData()) {
                // Id of the provider (ex: google.com)
                providerId = profile.getProviderId();
            }
            ;
        }
        if (providerId.equals("password")) {
            type = Constants.FIREBASE;
        } else if (providerId.equals("google.com")) {
            type = Constants.GOOGLE;
        } else if (providerId.equals("facebook.com")) {
            type = Constants.FACEBOOK;
        }
        return type;
    }


    private void updateNewPassword(String newPassword) {
        mAuth.getCurrentUser().updatePassword(newPassword)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            EventBus.getDefault().post(new EventPasswordUpdate(true, ""));
                        } else {
                            EventBus.getDefault().post(new EventPasswordUpdate(false, task.getException().getMessage()));
                        }
                    }
                });
    }

}
