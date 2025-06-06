package AutoBurp.bypass;

import AutoBurp.bypass.beens.*;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.Annotations;
import burp.api.montoya.core.HighlightColor;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.responses.analysis.Attribute;
import burp.api.montoya.proxy.ProxyHistoryFilter;
import burp.api.montoya.proxy.ProxyHttpRequestResponse;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;
import com.google.gson.Gson;

import javax.swing.*;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicBoolean;

import static burp.api.montoya.http.message.responses.analysis.AttributeType.WORD_COUNT;

public class Utilities {
    private static final String RESOURCE_BUNDLE = "strings";
    static AtomicBoolean unloaded = new AtomicBoolean(false);
    static MontoyaApi montoyaApi;
    static Gson gson;


    Utilities(MontoyaApi api) {
        montoyaApi = api;
        gson = new Gson();
    }

    static void updateProxySettings(MatchAndReplace rule) {
        String proxy = montoyaApi.burpSuite().exportProjectOptionsAsJson("proxy.match_replace_rules");
        ProxySettings currentProxySettings = gson.fromJson(proxy, ProxySettings.class);
        ProxySettings changedProxySettings = currentProxySettings.toggleMatchAndReplace(rule);
        String serializedProxySettings = gson.toJson(changedProxySettings);
        importProject(serializedProxySettings);
    }

    static void updateTLSSettings(String[] protocols, String[] ciphers) {
        String project_settings = montoyaApi.burpSuite().exportProjectOptionsAsJson("project_options");
        TLSSettings currentTLSSettings = gson.fromJson(project_settings, TLSSettings.class);
        currentTLSSettings.addProtocols(protocols);
        currentTLSSettings.addCiphers(ciphers);
        String serializedTLSSettings = gson.toJson(currentTLSSettings);
        importProject(serializedTLSSettings);
    }
    static void updateProxySettingsSync(MatchAndReplace rule) {
        String proxy = montoyaApi.burpSuite().exportProjectOptionsAsJson("proxy.match_replace_rules");
        ProxySettings currentProxySettings = gson.fromJson(proxy, ProxySettings.class);
        ProxySettings changedProxySettings = currentProxySettings.toggleMatchAndReplace(rule);
        String serializedProxySettings = gson.toJson(changedProxySettings);
        montoyaApi.burpSuite().importProjectOptionsFromJson(serializedProxySettings);
    }
    static void updateTLSSettingsSync(String[] protocols, String[] ciphers) {
        String project_settings = montoyaApi.burpSuite().exportProjectOptionsAsJson("project_options");
        TLSSettings currentTLSSettings = gson.fromJson(project_settings, TLSSettings.class);
        currentTLSSettings.addProtocols(protocols);
        currentTLSSettings.addCiphers(ciphers);
        String serializedTLSSettings = gson.toJson(currentTLSSettings);
        montoyaApi.burpSuite().importProjectOptionsFromJson(serializedTLSSettings);
    }
    static boolean enabledHTTPDowngrade() {
        String project_settings = montoyaApi.burpSuite().exportProjectOptionsAsJson("project_options");
        TLSSettings currentTLSSettings = gson.fromJson(project_settings, TLSSettings.class);
        return currentTLSSettings.enabledHTTPDowngrade();
    }
    static void updateHTTPSettings() {
        List<MatchAndReplace> rules = MatchAndReplace.createDowngradeRules();
        String proxy = montoyaApi.burpSuite().exportProjectOptionsAsJson("proxy.match_replace_rules");
        ProxySettings currentProxySettings = gson.fromJson(proxy, ProxySettings.class);
        ProxySettings changedProxySettings = currentProxySettings.toggleHTTPDowngradeMatchAndReplace(rules);
        String serializedProxySettings = gson.toJson(changedProxySettings);
        montoyaApi.burpSuite().importProjectOptionsFromJson(serializedProxySettings);

        String project_settings = montoyaApi.burpSuite().exportProjectOptionsAsJson("project_options");
        TLSSettings currentTLSSettings = gson.fromJson(project_settings, TLSSettings.class);
        currentTLSSettings.toggleHTTPSettings();
        String serializedTLSSettings = gson.toJson(currentTLSSettings);
        montoyaApi.burpSuite().importProjectOptionsFromJson(serializedTLSSettings);
    }

    static void importProject(String serializedSettings) {
        try {
            SwingUtilities.invokeAndWait(() -> {
                montoyaApi.burpSuite().importProjectOptionsFromJson(serializedSettings);
            });
        } catch (Exception ignored){}
    }


    static void saveTLSSettings() {
        String project_settings = montoyaApi.burpSuite().exportProjectOptionsAsJson("project_options");
        String proxy_settings = montoyaApi.burpSuite().exportProjectOptionsAsJson("proxy.match_replace_rules");
        montoyaApi.persistence().preferences().setString(Utilities.getResourceString("network_preferences"), project_settings);
        montoyaApi.persistence().preferences().setString(Utilities.getResourceString("proxy_preferences"), proxy_settings);
    }

    static void loadTLSSettings() {
        String project_settings = montoyaApi.persistence().preferences().getString(Utilities.getResourceString("network_preferences"));
        String proxy_settings = montoyaApi.persistence().preferences().getString(Utilities.getResourceString("proxy_preferences"));
        montoyaApi.burpSuite().importProjectOptionsFromJson(project_settings);
        montoyaApi.burpSuite().importProjectOptionsFromJson(proxy_settings);
    }

    static void log(String message) {
        montoyaApi.logging().logToOutput(message);
    }

    static void error(String message) {
        montoyaApi.logging().logToError(message);
    }

    static String getResourceString(String id) {
        return ResourceBundle.getBundle(RESOURCE_BUNDLE).getString(id);
    }

    public static boolean doesHostExist(String urlString) {
        try {
            URI url = new URI(urlString);
            String host = url.getHost();
            InetAddress address = InetAddress.getByName(host);
            return address != null;
        } catch (Exception e) {
            return false;
        }
    }

    static HttpRequestResponse attemptRequest(HttpRequestResponse requestResponse, String negotiation) {
        if (unloaded.get()) {
            log("Extension unloaded - aborting attack");
            throw new RuntimeException("Extension unloaded");
        } else {
            try {
                if (doesHostExist(requestResponse.request().url())) {
                    return montoyaApi.http().sendRequest(requestResponse.request()).withAnnotations(Annotations.annotations(negotiation));
                }
                return null;
            } catch (Exception e) {
                error(e.getMessage());
                return null;
            }
        }
    }

    static HttpRequestResponse unpredictable(List<HttpRequestResponse> comparableResponses) {
        HttpRequestResponse etalon = null;
        double P = 0.2;
        int base = -1;
        for(HttpRequestResponse requestResponse: comparableResponses) {
            if (requestResponse.hasResponse() && requestResponse.response() != null) {
                Optional<Integer> words = requestResponse.response().attributes(WORD_COUNT).stream().map(Attribute::value).findFirst();
                if (words.isPresent()) {
                    if (base == -1) {
                        base = words.get();
                        etalon = requestResponse;
                    } else {
                        int diff = Math.abs(base - words.get());
                        if (diff > Math.abs(base) * P) etalon = requestResponse;
                    }
                }
            }
        }
        return etalon;
    }

    static boolean compareResponses(HttpRequestResponse baseRequest, List<HttpRequestResponse> comparableResponses) {
        HttpRequestResponse requestResponse = unpredictable(comparableResponses);
        if (baseRequest.response() == null || requestResponse == null) return false;
        double P = 0.2;
        int b = 0;
        int c = 0;
        List<Attribute> baseAttributes = baseRequest.response().attributes(WORD_COUNT);
        List<Attribute> comparableAttributes = requestResponse.response().attributes(WORD_COUNT);
        Optional<Integer> baseWordCount = baseAttributes.stream().map(Attribute::value).findFirst();
        Optional<Integer> comparableWordCount = comparableAttributes.stream().map(Attribute::value).findFirst();
        if (baseWordCount.isPresent() && comparableWordCount.isPresent()) {
            b = baseWordCount.get();
            c = comparableWordCount.get();
        } else {
            b = baseRequest.response().headers().size();
            c = requestResponse.response().headers().size();
        }
        int diff = Math.abs(b - c);
        return diff > Math.abs(b) * P || diff > Math.abs(c) * P;
    }

    static String stringify(Object o) {
        return gson.toJson(o);
    }

    static String negotiation(String[] protocols, String[] ciphers) {
        return stringify(new TLSSettings(new ProjectOptions(new SSL(new Negotiation( ciphers, protocols, true)))));
    }

    static void addComment(HttpRequestResponse baseRequest, String comments) {
        List<ProxyHttpRequestResponse> items = montoyaApi.proxy().history(new ProxyHistoryFilter() {
            @Override
            public boolean matches(ProxyHttpRequestResponse requestResponse) {
                return requestResponse.finalRequest().equals(baseRequest.request());
            }
        });
        items.forEach(item -> {
            item.annotations().setNotes(comments);
            item.annotations().setHighlightColor(HighlightColor.RED);
        });
    }
    static String getComment(HttpRequestResponse baseRequest) {
        List<ProxyHttpRequestResponse> items = montoyaApi.proxy().history(new ProxyHistoryFilter() {
            @Override
            public boolean matches(ProxyHttpRequestResponse requestResponse) {
                return requestResponse.hasResponse() && baseRequest.hasResponse() && requestResponse.originalResponse().equals(baseRequest.response()) &&
                        requestResponse.annotations().hasNotes();
            }
        });
        Optional<ProxyHttpRequestResponse> optionalItem = items.stream().findFirst();
        if (optionalItem.isEmpty()) return null;
        String negotiation = optionalItem.get().annotations().notes();
        try {
            TLSSettings validator = gson.fromJson(negotiation, TLSSettings.class);
            return stringify(validator);
        }catch (Exception ignored){
            return null;
        }
    }

}
