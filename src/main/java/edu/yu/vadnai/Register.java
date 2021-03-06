package edu.yu.vadnai;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.util.Date;
import java.util.GregorianCalendar;

public class Register {
    /**
     * These static variables must be checked and updated each semester
     */
    private final static int term = 202209; // UPDATED MAY 2022
    private final static String urlBase = "https://banner.oci.yu.edu/ssb/";

    public static void main(String[] args) throws URISyntaxException, IOException, InterruptedException {
        Register registrationObject = new Register("YUID", "BANNERPIN",
                new GregorianCalendar(2022, 5 - 1, 6, 11, 0).getTime());
        String[] crns = { "12345", "67890", "111213", "141516" };
        registrationObject.register(crns);
        System.out.println(System.currentTimeMillis() + " completed");
    }

    /**
     * Performs Registration
     * 
     * crns accepts up to 10 entries, additional entries will not be used.
     * 
     * @param crns
     * @throws URISyntaxException
     * @throws IOException
     * @throws InterruptedException
     */
    public void register(String[] crns) throws URISyntaxException, IOException, InterruptedException {
        this.authenticate();
        this.selectTerm();
        this.sendRegistrationPayload(crns);
    }

    /**
     * @param userID
     * @param userPin
     * @param registrationTime
     */
    public Register(String userID, String userPin, Date registrationTime) {
        this.userID = userID;
        this.userPin = userPin;
        this.client = HttpClient.newHttpClient();
        this.registrationTime = registrationTime;
    }

    private final String userID;
    private final String userPin;
    private final HttpClient client;
    private final Date registrationTime;
    private String ssid;

    /**
     * Authenticates the user with Banner and saves session ID for future
     * transactions.
     * 
     * @throws URISyntaxException
     * @throws IOException
     * @throws InterruptedException
     */

    private synchronized void authenticate() throws URISyntaxException, IOException, InterruptedException {
        // Get Login Page - implicitly verifies url is valid
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
        requestBuilder.uri(new URI(urlBase + "twbkwbis.P_WWWLogin"));
        requestBuilder.GET();
        HttpResponse<String> response = client.send(requestBuilder.build(), BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Initial Get Failed - returned status code " + response.statusCode()
                    + "\n HTML: \n" + response.body());
        }

        // Send Credentials for Login
        requestBuilder = HttpRequest.newBuilder();
        requestBuilder.POST(BodyPublishers.ofString("sid=" + userID + "&PIN=" + userPin));
        requestBuilder.header("Cookie", "TESTID=set; POKEHAYU=SRV_1");
        requestBuilder.header("Content-Type", "application/x-www-form-urlencoded");
        requestBuilder.uri(new URI(urlBase + "twbkwbis.P_ValLogin"));
        response = client.send(requestBuilder.build(), BodyHandlers.ofString());
        if (response.statusCode() != 200 || response.body().contains("Authorization Failure")) {
            throw new IllegalStateException(
                    "Authentication Failed, likely bad credentials - returned status code " + response.statusCode()
                            + "\n HTML: \n" + response.body());
        }
        this.ssid = getSessid(response);
    }

    /**
     * Selects Registration Term
     * 
     * @throws URISyntaxException
     * @throws IOException
     * @throws InterruptedException
     */
    private synchronized void selectTerm() throws URISyntaxException, IOException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
        requestBuilder.uri(new URI(urlBase + "bwskflib.P_SelDefTerm"));
        requestBuilder.GET();
        requestBuilder.header("Cookie", "TESTID=set; " + this.ssid + " POKEHAYU=SRV_1");
        HttpResponse<String> response = client.send(requestBuilder.build(), BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Failed \n HTML: \n" + response.body());
        }
        getSessid(response);
        requestBuilder = HttpRequest.newBuilder();
        requestBuilder.uri(new URI(urlBase + "bwcklibs.P_StoreTerm"));
        requestBuilder.POST(BodyPublishers.ofString("name_var=bmenu.P_RegMnu&term_in=" + term));
        requestBuilder.header("Cookie", "TESTID=set; " + this.ssid + " POKEHAYU=SRV_1");
        requestBuilder.header("Content-Type", "application/x-www-form-urlencoded");
        response = client.send(requestBuilder.build(), BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Failed \n HTML: \n" + response.body());
        }
        this.ssid = getSessid(response);
    }

    private synchronized void waitForGoTime() throws InterruptedException {
        while (registrationTime.after(new Date())) {
            TimeUnit time = TimeUnit.SECONDS;
            long difference = time.convert(registrationTime.getTime() - new Date().getTime(), TimeUnit.MILLISECONDS);
            System.out.println("waiting another " + difference + " seconds");
            Thread.sleep(100);
        }
    }

    /**
     * Performs registration process.
     * 
     * @param crns
     * @throws IOException
     * @throws InterruptedException
     * @throws URISyntaxException
     */
    private synchronized void sendRegistrationPayload(String[] crns)
            throws IOException, InterruptedException, URISyntaxException {

        waitForGoTime();

        System.out.println("Registering Now...");
        long time = System.currentTimeMillis();

        // Get Registration Page

        HttpRequest.Builder homePageBuilder = HttpRequest.newBuilder();
        homePageBuilder.uri(new URI(urlBase + "bwskfreg.P_AltPin"));
        homePageBuilder.GET();
        homePageBuilder.header("Cookie", "TESTID=set; " + this.ssid + " POKEHAYU=SRV_1");
        homePageBuilder.header("Content-Type", "application/x-www-form-urlencoded");
        HttpResponse<String> response = client.send(homePageBuilder.build(), BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Failed \n HTML: \n" + response.body());
        }
        getSessid(response);
        List<HashMap<String, String>> records = new LinkedList<>();
        getExistingRecords(response, records);

        // Post Registration CRN

        HttpRequest.Builder register = HttpRequest.newBuilder();
        register.uri(new URI(urlBase + "bwckcoms.P_Regs"));
        register.header("Cookie", "TESTID=set; " + this.ssid + " POKEHAYU=SRV_1");
        register.header("Content-Type", "application/x-www-form-urlencoded");
        register.POST(BodyPublishers.ofString(buildCRNPayload(crns, records)));
        response = client.send(register.build(), BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Failed \n HTML: \n" + response.body());
        }

        // Print stats

        System.out.println("Elapsed Time: " + (System.currentTimeMillis() - time) + " ms");
        System.out.println();
        System.out.println("Successfully Registered for:");
        printCurrentCourses(response);
        System.out.println();
        System.out.println();
        System.out.println("Error Messages for:");
        printErrorTable(response);
    }

    private static void printCurrentCourses(HttpResponse<String> response) {
        String html = response.body();
        Document doc = Jsoup.parse(html);
        Elements tableElements = doc.select("body > div.pagebodydiv > form > table:nth-child(18)");
        Elements tableHeaderEles = tableElements.select("th");
        StringBuilder th = new StringBuilder();
        for (int i = 0; i < tableHeaderEles.size(); i++) {

            th.append(tableHeaderEles.get(i).text());
            th.append(" ");
        }
        System.out.println(th);

        Elements tableRowElements = tableElements.select(":not(thead) tr");
        for (int i = 0; i < tableRowElements.size(); i++) {
            Element row = tableRowElements.get(i);
            Elements rowItems = row.select("td");
            StringBuilder a = new StringBuilder();
            for (int j = 0; j < rowItems.size(); j++) {
                a.append(rowItems.get(j).text() + " ");
            }
            System.out.println(" " + a);
        }
    }

    private static void printErrorTable(HttpResponse<String> response) {
        String html = response.body();
        Document doc = Jsoup.parse(html);
        Elements tableElements = doc.select("body > div.pagebodydiv > form > table:nth-child(23)");
        Elements tableHeaderEles = tableElements.select("th");
        StringBuilder th = new StringBuilder();
        for (int i = 0; i < tableHeaderEles.size(); i++) {

            th.append(tableHeaderEles.get(i).text());
            th.append(" ");
        }
        System.out.println(th);

        Elements tableRowElements = tableElements.select(":not(thead) tr");
        for (int i = 0; i < tableRowElements.size(); i++) {
            Element row = tableRowElements.get(i);
            Elements rowItems = row.select("td");
            StringBuilder a = new StringBuilder();
            for (int j = 0; j < rowItems.size(); j++) {
                a.append(rowItems.get(j).text() + " ");
            }
            System.out.println(" " + a);
        }
    }

    private static String getSessid(HttpResponse<String> response) {
        return response.headers().map().get("set-cookie").get(0);
    }

    private static void getExistingRecords(HttpResponse<String> response, List<HashMap<String, String>> records) {
        Document doc = Jsoup.parse(response.body());
        Elements elements = doc.select("body > div.pagebodydiv > form > table.datadisplaytable");
        if (elements.size() == 0) {
            return;
        }
        Element table = elements.first().child(0);
        table.child(0).remove();
        for (Element row : table.children()) {
            Elements inputs = row.getElementsByTag("input");
            HashMap<String, String> map = new HashMap<>();
            for (Element e : inputs) {
                map.put(e.attr("name").trim(), e.val().trim());
            }
            records.add(map);
        }
    }

    private static String buildCRNPayload(String[] crns, List<HashMap<String, String>> records) {
        String crn1 = "";
        String crn2 = "";
        String crn3 = "";
        String crn4 = "";
        String crn5 = "";
        String crn6 = "";
        String crn7 = "";
        String crn8 = "";
        String crn9 = "";
        String crn10 = "";

        if (crns.length >= 1) {
            crn1 = crns[0];
        }

        if (crns.length >= 2) {
            crn2 = crns[1];
        }

        if (crns.length >= 3) {
            crn3 = crns[2];
        }

        if (crns.length >= 4) {
            crn4 = crns[3];
        }

        if (crns.length >= 5) {
            crn5 = crns[4];
        }

        if (crns.length >= 6) {
            crn6 = crns[5];
        }

        if (crns.length >= 7) {
            crn7 = crns[6];
        }

        if (crns.length >= 8) {
            crn8 = crns[7];
        }

        if (crns.length >= 9) {
            crn9 = crns[8];
        }

        if (crns.length >= 10) {
            crn10 = crns[9];
        }

        StringBuilder sb = new StringBuilder();
        sb.append("term_in=" + term);
        sb.append("&");
        sb.append("RSTS_IN=DUMMY");
        sb.append("&");
        sb.append("assoc_term_in=DUMMY");
        sb.append("&");
        sb.append("CRN_IN=DUMMY");
        sb.append("&");
        sb.append("start_date_in=DUMMY");
        sb.append("&");
        sb.append("end_date_in=DUMMY");
        sb.append("&");
        sb.append("SUBJ=DUMMY");
        sb.append("&");
        sb.append("CRSE=DUMMY");
        sb.append("&");
        sb.append("SEC=DUMMY");
        sb.append("&");
        sb.append("LEVL=DUMMY");
        sb.append("&");
        sb.append("CRED=DUMMY");
        sb.append("&");
        sb.append("GMOD=DUMMY");
        sb.append("&");
        sb.append("TITLE=DUMMY");
        sb.append("&");
        sb.append("MESG=DUMMY");
        sb.append("&");
        sb.append("REG_BTN=DUMMY");
        sb.append("&");
        sb.append("MESG=DUMMY");
        sb.append("&");
        // END OF PREAMBLE
        sb.append(buildRecords(records));

        // NEW
        sb.append("RSTS_IN=RW");
        sb.append("&");
        sb.append("CRN_IN=" + crn1);
        sb.append("&");
        sb.append("assoc_term_in=");
        sb.append("&");
        sb.append("start_date_in=");
        sb.append("&");
        sb.append("end_date_in=");
        sb.append("&");
        sb.append("RSTS_IN=RW");
        sb.append("&");
        sb.append("CRN_IN=" + crn2);
        sb.append("&");
        sb.append("assoc_term_in=");
        sb.append("&");
        sb.append("start_date_in=");
        sb.append("&");
        sb.append("end_date_in=");
        sb.append("&");
        sb.append("RSTS_IN=RW");
        sb.append("&");
        sb.append("CRN_IN=" + crn3);
        sb.append("&");
        sb.append("assoc_term_in=");
        sb.append("&");
        sb.append("start_date_in=");
        sb.append("&");
        sb.append("end_date_in=");
        sb.append("&");
        sb.append("RSTS_IN=RW");
        sb.append("&");
        sb.append("CRN_IN=" + crn4);
        sb.append("&");
        sb.append("assoc_term_in=");
        sb.append("&");
        sb.append("start_date_in=");
        sb.append("&");
        sb.append("end_date_in=");
        sb.append("&");
        sb.append("RSTS_IN=RW");
        sb.append("&");
        sb.append("CRN_IN=" + crn5);
        sb.append("&");
        sb.append("assoc_term_in=");
        sb.append("&");
        sb.append("start_date_in=");
        sb.append("&");
        sb.append("end_date_in=");
        sb.append("&");
        sb.append("RSTS_IN=RW");
        sb.append("&");
        sb.append("CRN_IN=" + crn6);
        sb.append("&");
        sb.append("assoc_term_in=");
        sb.append("&");
        sb.append("start_date_in=");
        sb.append("&");
        sb.append("end_date_in=");
        sb.append("&");
        sb.append("RSTS_IN=RW");
        sb.append("&");
        sb.append("CRN_IN=" + crn7);
        sb.append("&");
        sb.append("assoc_term_in=");
        sb.append("&");
        sb.append("start_date_in=");
        sb.append("&");
        sb.append("end_date_in=");
        sb.append("&");
        sb.append("RSTS_IN=RW");
        sb.append("&");
        sb.append("CRN_IN=" + crn8);
        sb.append("&");
        sb.append("assoc_term_in=");
        sb.append("&");
        sb.append("start_date_in=");
        sb.append("&");
        sb.append("end_date_in=");
        sb.append("&");
        sb.append("RSTS_IN=RW");
        sb.append("&");
        sb.append("CRN_IN=" + crn9);
        sb.append("&");
        sb.append("assoc_term_in=");
        sb.append("&");
        sb.append("start_date_in=");
        sb.append("&");
        sb.append("end_date_in=");
        sb.append("&");
        sb.append("RSTS_IN=RW");
        sb.append("&");
        sb.append("CRN_IN=" + crn10);
        sb.append("&");
        sb.append("assoc_term_in=");
        sb.append("&");
        sb.append("start_date_in=");
        sb.append("&");
        sb.append("end_date_in=");
        sb.append("&");
        sb.append("regs_row=" + records.size());
        sb.append("&");
        sb.append("wait_row=0");
        sb.append("&");
        sb.append("add_row=10");
        sb.append("&");
        sb.append("REG_BTN=Submit+Changes");
        return sb.toString();
    }

    private static String buildRecords(List<HashMap<String, String>> records) {
        StringBuilder sb = new StringBuilder();
        for (HashMap<String, String> m : records) {
            sb.append("RSTS_IN=");
            sb.append("&");
            sb.append("assoc_term_in=" + m.get("assoc_term_in"));
            sb.append("&");
            sb.append("CRN_IN=" + m.get("CRN_IN"));
            sb.append("&");
            sb.append("start_date_in=" + m.get("start_date_in").replace("/", "%2F"));
            sb.append("&");
            sb.append("end_date_in=" + m.get("end_date_in").replace("/", "%2F"));
            sb.append("&");
            sb.append("SUBJ=" + m.get("SUBJ"));
            sb.append("&");
            sb.append("CRSE=" + m.get("CRSE"));
            sb.append("&");
            sb.append("SEC=" + m.get("SEC"));
            sb.append("&");
            sb.append("LEVL=" + m.get("LEVL"));
            sb.append("&");
            sb.append("CRED=++++" + m.get("CRED"));
            sb.append("&");
            sb.append("GMOD=" + m.get("GMOD").replace(" ", "+"));
            sb.append("&");
            sb.append("TITLE=" + m.get("TITLE").replace(" ", "+"));
            sb.append("&");
            if (!m.equals(records.get(records.size() - 1))) {
                sb.append("MESG=DUMMY");
                sb.append("&");
            }

        }
        return sb.toString();
    }
}
