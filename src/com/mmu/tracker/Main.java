package com.mmu.tracker;
//import required libraries
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.HttpResponse;
import kong.unirest.UnirestException;
import kong.unirest.json.JSONArray;
import kong.unirest.json.JSONException;
import kong.unirest.json.JSONObject;
import org.jetbrains.annotations.Nullable;
import javax.swing.*;
import java.util.Objects;

public class Main {
    private JPanel mainPanel;
    @SuppressWarnings("unused")
    private SearchBar<String> searchBar;
    @SuppressWarnings("unused")
    private JButton refreshButton;
    @SuppressWarnings("unused")
    private JLabel lblRecoveryAll;
    @SuppressWarnings("unused")
    private JLabel lblRecoveryNew;
    @SuppressWarnings("unused")
    private JLabel lblCaseAll;
    @SuppressWarnings("unused")
    private JLabel lblCaseNew;
    @SuppressWarnings("unused")
    private JLabel lblDeathAll;
    @SuppressWarnings("unused")
    private JLabel lblDeathNew;
    private static String requestRegion;
    private static boolean hasConnError = false;

    /*combobox code inspired by:
    https://kodejava.org/how-do-i-set-and-get-the-selected-item-in-jcombobox/*/
    public Main() {
        //equivalent to Unirest.setTimeouts(0, 0); in older unirest-java
        Unirest.config().socketTimeout(0).connectTimeout(0);
        //we load the data here to avoid hell
        loadData(searchBar);
        //query the data
        searchBar.addActionListener(actionEvent -> loadRegion(
                Objects.requireNonNull(searchBar.getSelectedItem()).toString(),
                lblCaseAll,
                lblCaseNew, lblDeathAll,
                lblDeathNew
        ));
        //refresh data in case it's too old
        refreshButton.addActionListener(actionEvent -> loadData(searchBar));
    }
    //main code block
    public static void main(String[] args) {
        //load and display the form
        JFrame frame = new JFrame("TriCovid");
        frame.setContentPane(new Main().mainPanel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }
    //deduplicate connection error messages
    private static void connError(){
        System.out.println(
                "failed to download data, is your internet connection working?"
        );
        hasConnError = true;
    }
    //do not start apirequest with a slash!
    @org.jetbrains.annotations.Nullable
    static JsonNode download(String apiRequest){
        HttpResponse<JsonNode> response;
        //retrieve data from online api
        if(!hasConnError){
            try {
                response = Unirest.get(
                        "https://api.ukhsa-dashboard.data.gov.uk/themes/infectious_disease/sub_themes/respiratory/topics/COVID-19/geography_types/Lower%20Tier%20Local%20Authority/geographies"
                                + apiRequest
                ).asJson();
            } catch (
                    UnirestException e
            ) {
                connError();
                return null;
            }
        } else {
            return null;
        }
        //we must use getbody to get the part we care about
        return response.getBody();
    }
    static void loadData(JComboBox<String> searchBar){
        //make sure variable is accessible
        JSONArray regions;
        if (!hasConnError) {
            try {
                //get data
                JsonNode response = download(
                        ""
                );
                if (response == null) {
                    System.out.println(
                            "no data returned, has the api changed?"
                    );
                    return;
                }
                regions = response
                        .getArray();
                //iterate over all the regions
                for(Object region : regions){
                    //add each region to the dropdown list
                    searchBar.addItem(
                            (
                                    (JSONObject) region
                            ).getString(
                                    "name"
                            )
                    );
                }
            } catch (JSONException | NullPointerException e) {
                System.out.println(
                        "json parsing failed, is data correct? try clicking refresh"
                );
            }
        }
    }
    static @Nullable JSONObject getData(String metric) {
        String dlString=
                requestRegion
                        +
                        metric
                        +
                        "?page_size=1"
                ;

        // get the last page which contains the latest metric
        JsonNode response = download(
                dlString
        );
        if (response == null) {
            connError();
            return null;
        }

        JsonNode lastPage =
                download(
                        dlString
                                +
                                "&page="
                                +
                                // get the count of results
                                response
                                        .getObject()
                                        .getInt(
                                                "count"
                                        )
                        )
                ;

        if (lastPage == null) {
            System.out.println(
                    "failed to load the latest data, possibly invalid request"
            );
            return null;
        }

        return
                lastPage
                        .getObject()
                        .getJSONArray(
                                "results"
                        ).getJSONObject(
                                0
                        )
                ;
    }

    static void loadRegion(
            String searchBarTxt,
            JLabel lblCaseAll,
            JLabel lblCaseNew,
            JLabel lblDeathAll,
            JLabel lblDeathNew
    ){
        //sentinel value
        boolean found = false;
        //make sure there's a string
        if(searchBarTxt != null && !searchBarTxt.isEmpty()){
            try {
                requestRegion = "/"
                        +
                        searchBarTxt
                        +
                        "/metrics/"
                ;
                JSONObject deaths = getData(
                        "COVID-19_deaths_ONSByWeek"
                );
                JSONObject cases = getData(
                        "COVID-19_testing_positivity7DayRolling"
                );

                if (deaths == null || cases == null) {
                    System.out.println(
                            "unable to load all requested data, is the region valid?"
                    );
                } else {
                    lblCaseAll
                            .setText(
                                    String.valueOf(
                                            cases
                                                    .getDouble(
                                                            "metric_value"
                                                    )
                                    )
                            )
                    ;
                    lblCaseNew
                            .setText(
                                    cases
                                            .getString(
                                                    "date"
                                            )
                            )
                    ;
                    lblDeathAll
                            .setText(
                                    String.valueOf(
                                            deaths
                                                    .getDouble(
                                                            "metric_value"
                                                    )
                                    )
                            )
                    ;
                    lblDeathNew
                            .setText(
                                    deaths
                                            .getString(
                                                    "date"
                                            )
                            )
                    ;
                    found = true;
                }
            } catch (Exception e) {
                System.out.println("Failed to load region data: " + e.getMessage());
            }
        }
        if(!found) {
            lblCaseAll.setText("invalid");
            lblDeathAll.setText("location");
            lblCaseNew.setText("try");
            lblDeathNew.setText("again");
        }
    }
    //test function to review output. do not start apirequest with a slash!
//    static void print(String apiRequest) {
//        System.out.println(download(apiRequest));
//    }
}
