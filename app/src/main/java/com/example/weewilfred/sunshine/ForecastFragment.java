/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.example.weewilfred.sunshine;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.SharedPreferences;
import android.icu.text.SimpleDateFormat;
import android.icu.util.GregorianCalendar;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;



import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class ForecastFragment extends Fragment {

    private ArrayAdapter<String> mForecastAdapter;

    public ForecastFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        //Add this line in order for this fragment to handle menu events
        setHasOptionsMenu(true);
    }
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater){
        inflater.inflate(R.menu.forecastfragment, menu);
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        //Handle action bar item clicks here. The action bar will
        //automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_refresh){
            updateWeather();

            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override //This "onCreateView" method is where the UI gets initialized
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        /*String[] data = {
                "Monday - Sunny - 14/24",
                "Tuesday - Cloudy - 10/19",
                "Wednesday - Solar Flares - -45/134",
                "Thursday - Everyone is Dead - ?",
                "Friday - Everyone gone - gone.. gone",
                "Saturday - Glowing Green - 22/24",
                "Sunday - Excellent weather for Zombies"
        }; DUMMY DATA

        List<String> weekForecast = new ArrayList<String>(Arrays.asList(data));   DUMMY DATA*/

        // Get a reference to the ListView, and attach this adapter to it.
         mForecastAdapter =
                new ArrayAdapter<String>(
                        //The current context (This fragments parent activity)
                        getActivity(),
                        //ID of list item layout style
                        R.layout.list_item_forecast,
                        //ID of the textview to populate the layout
                        R.id.list_item_forecast_textview,
                        // Pass in Forecast Data To populate the text
                        new ArrayList<String>());

        View rootView = inflater.inflate(R.layout.fragment_main, container);

        //Set the mForecast Adapter to send its adapted info to the listview
        ListView listView = (ListView) rootView.findViewById(
                R.id.listview_forecast);
        listView.setAdapter(mForecastAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView,  View view, int position, long l ) {
                String forecast = mForecastAdapter.getItem(position);
                //Toast.makeText(getActivity(), forecast, Toast.LENGTH_SHORT).show();
                Intent DetailIntent = new Intent(getActivity(), DetailActivity.class).putExtra(Intent.EXTRA_TEXT, forecast);
                startActivity(DetailIntent);
            }
        });


        return rootView;
    }
    private void updateWeather(){
        FetchWeatherTask weatherTask = new FetchWeatherTask();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String location = prefs.getString(getString(R.string.pref_location_key),getString(R.string.pref_location_default));
        weatherTask.execute(location);
    }

    @Override               //Sets the app to update weather on startup. Note the super.onStart allows this method to use the parent (aka super)
    public void onStart(){  //method called onStart();
        super.onStart();
        updateWeather();
    }

    public class FetchWeatherTask extends AsyncTask<String, Void, String[]> {
        /* The system calls this to perform work in a worker thread and delivers it the parameters given to AsyncTask.execute();*/
        private final String LOG_TAG = FetchWeatherTask.class.getSimpleName();

        /* The date/time conversion code is going to be moved outside the asynctask later,
        * so for convenience we're breaking it out into its own method now.*/

        /**
         * Prepare the weather high/lows for presentation.
         */
        private String formatHighLows(double high, double low) {
            // For presentation, assume the user doesn't care about tenths of a degree.
            SharedPreferences sharedPrefs =
                    PreferenceManager.getDefaultSharedPreferences(getActivity());
            String unitType = sharedPrefs.getString(
                    getString(R.string.pref_units_key),
                    getString(R.string.pref_units_metric));

            if (unitType.equals(getString(R.string.pref_units_Kelvin))){
                high = high + 272;
                low = low + 272;
            } else if (!unitType.equals(getString(R.string.pref_units_metric))){
                Log.d(LOG_TAG, "Unit Type not found: " + unitType);
            }

            long roundedHigh = Math.round(high);
            long roundedLow = Math.round(low);

            String highLowStr = roundedHigh + "/" + roundedLow;
            return highLowStr;
        }

        /**
         * Take the String representing the complete forecast in JSON Format and
         * pull out the data we need to construct the Strings needed for the wireframes.
         *
         * Fortunately parsing is easy:  constructor takes the JSON string and converts it
         * into an Object hierarchy for us.
         */
        @TargetApi(Build.VERSION_CODES.N)
        private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays)
                throws JSONException {

            // These are the names of the JSON objects that need to be extracted.
            final String OWM_LIST = "list";
            final String OWM_WEATHER = "weather";
            final String OWM_TEMPERATURE = "temp";
            final String OWM_MAX = "temp_max";
            final String OWM_MIN = "temp_min";
            final String OWM_DESCRIPTION = "main";

            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

            // OWM returns daily forecasts based upon the local time of the city that is being
            // asked for, which means that we need to know the GMT offset to translate this data
            // properly.

            String[] resultStrs = new String[numDays];
            for(int i = 0; i < weatherArray.length(); i++) {
                // For now, using the format "Day, description, hi/low"
                String day;
                String description;
                String highAndLow;

                // The date/time is returned as a long.  We need to convert that
                // into something human-readable, since most people won't read "1400356800" as
                // "this saturday".

                //create a Gregorian Calendar, which is in current date
                GregorianCalendar gc = new GregorianCalendar();
                //add i dates to current date of calendar
                gc.add(GregorianCalendar.DATE, i);
                //get that date, format it, and "save" it on variable day
                Date time = gc.getTime();
                SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE MMM dd");
                day = shortenedDateFormat.format(time);


                // Get the JSON object representing the day
                JSONObject dayForecast = weatherArray.getJSONObject(i);
                JSONObject dayMain = dayForecast.getJSONObject(OWM_DESCRIPTION);
                // description is in a child array called "weather", which is 1 element long.
                JSONObject weatherObject = dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
                description = weatherObject.getString(OWM_DESCRIPTION);

                // Temperatures are in a child object called "temp".  Try not to name variables
                // "temp" when working with temperature.  It confuses everybody.
               // double temperatureObject = dayMain.getDouble(OWM_TEMPERATURE);
                double high = dayMain.getDouble(OWM_MAX);
                double low = dayMain.getDouble(OWM_MIN);

                highAndLow = formatHighLows(high, low);
                resultStrs[i] = day + " - " + description + " - " + highAndLow;
            }
            for (String s : resultStrs) {
                Log.v(LOG_TAG, "Forecast entry: " + s);
            }
            return resultStrs;
        }

        @Override
        protected String[] doInBackground(String... params){
            // These two need to be declared outside the try/catch
            // so that they can be closed in the finally block.

            if (params.length == 0){
                return null;
            }

            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            // Will contain the raw JSON response as a string.
            String forecastJsonStr = null;

            //Add code for URL builder functions here
            //How the URI builder is labelled <scheme>://<authority><absolute path>?<query>#<fragment>
            String format = "json";
            String units = "metric";
            int numDays = 7;
            String appid = "dde27f8876ae58fb1c7259bb360ee180";

            try {
                // Construct the URL for the OpenWeatherMap query
                // Possible parameters are avaiable at OWM's forecast API page, at
                // http://openweathermap.org/API#forecast
                final String FORECAST_BASE_URL = "http://api.openweathermap.org/data/2.5/forecast/city?";
                final String CITY_PARAM = "id";
                final String FORMAT_PARAM = "mode";
                final String UNITS_PARAM = "units";
                final String DAYS_PARAM = "cnt";
                final String APPID_PARAM = "APPID";

                // The URL to be built "http://api.openweathermap.org/data/2.5/forecast/
                // city?id=7839581&mode=json&units=metric&cnt=7&APPID=dde27f8876ae58fb1c7259bb360ee180"
                Uri OpenWeatherURL = Uri.parse(FORECAST_BASE_URL).buildUpon()
                        .appendQueryParameter(CITY_PARAM, params[0])        //params[0] == city id that user can enter
                        .appendQueryParameter(FORMAT_PARAM, format)
                        .appendQueryParameter(UNITS_PARAM, units)
                        .appendQueryParameter(DAYS_PARAM, Integer.toString(numDays))
                        .appendQueryParameter(APPID_PARAM, appid)
                        .build();

                URL url = new URL(OpenWeatherURL.toString());

                // Create the request to OpenWeatherMap, and open the connection
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                // Read the input stream into a String
                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    // Nothing to do.
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                    // But it does make debugging a *lot* easier if you print out the completed
                    // buffer for debugging.
                    buffer.append(line + "\n");
                }

                if (buffer.length() == 0) {
                    // Stream was empty.  No point in parsing.
                    return null;
                }

                forecastJsonStr = buffer.toString();
                Log.v(LOG_TAG, "Forecast JSON String:" + forecastJsonStr );

            } catch (IOException e) {
                Log.e("LOG_TAG", "Error ", e);
                // If the code didn't successfully get the weather data, there's no point in attemping
                // to parse it.
                return null;

            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e("LOG_TAG", "Error closing stream", e);
                    }
                }
            }

            try {
                return getWeatherDataFromJson(forecastJsonStr, numDays);
            } catch (JSONException e) {
                Log.e(LOG_TAG, e.getMessage(), e);
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(String[] result) {
            if (result != null) {
                Log.v(LOG_TAG, "This is the result" + result);
                mForecastAdapter.clear();
                for (String dayForecastStr : result) {
                    mForecastAdapter.add(dayForecastStr);
                }
            }
        }
    }
}

