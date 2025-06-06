package AutoBurp.bypass;

import AutoBurp.bypass.beens.Browsers;
import AutoBurp.bypass.beens.MatchAndReplace;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.responses.HttpResponse;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.ThreadPoolExecutor;

import static AutoBurp.bypass.Constants.MAX_ATTEMPTS;

public class TriggerCipherGuesser implements ActionListener, Runnable {
    private ThreadPoolExecutor taskEngine;
    private final List<HttpRequestResponse> requestResponses;

    public TriggerCipherGuesser(ThreadPoolExecutor taskEngine, List<HttpRequestResponse> requestResponses) {
        this.taskEngine = taskEngine;
        this.requestResponses = requestResponses;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if(requestResponses.isEmpty()) return;
        (new Thread(this)).start();
    }


    @Override
    public void run() {
            taskEngine.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        Utilities.log(String.format("|*| Starting attack on %s targets", requestResponses.size()));
                        Utilities.loadTLSSettings();
                        ListIterator<HttpRequestResponse> it = requestResponses.listIterator();
                        for(String[] protocol : Constants.BRUTEFORCE_CIPHERS.keySet()) {
                            if(!it.hasNext()) {
                                Utilities.log("|*| Nothing to do!");
                                break;
                            }
                            String[] ciphers = Constants.BRUTEFORCE_CIPHERS.get(protocol);
                            Utilities.updateTLSSettings(protocol, ciphers);
                            Utilities.log(String.format("|*| Probing protocols: %s", Utilities.stringify(protocol)));
                            while (it.hasNext()) {
                                HttpRequestResponse requestResponse = it.next();
                                String negotiation = Utilities.negotiation(protocol,ciphers);
                                List<HttpRequestResponse> probs = new ArrayList<>();
                                for(int i = 0; i < MAX_ATTEMPTS; i++) {
                                    HttpRequestResponse prob = Utilities.attemptRequest(requestResponse, negotiation);
                                    probs.add(prob);
                                }
                                if ( !probs.isEmpty() && Utilities.compareResponses(requestResponse, probs)) {
                                    String comment = String.format(
                                            "|*| URL %s response was changed. Status code %s. TLS settings: %s",
                                            requestResponse.request().url(),
                                            probs.stream()
                                                    .map(HttpRequestResponse::response)
                                                    .map(HttpResponse::statusCode)
                                                    .map(String::valueOf)
                                                    .reduce("",(partial,element) -> element + "," + partial),
                                            negotiation );
                                    Utilities.log(comment);
                                    Utilities.addComment(requestResponse,negotiation);
                                    it.remove();
                                }
                            }
                            while (it.hasPrevious()) {
                                it.previous();
                            }
                            Thread.sleep(100);
                        }

                    }catch (Exception e) {
                        Utilities.log(e.getMessage());
                    }
                    finally {
                        Utilities.updateTLSSettingsSync(Constants.BROWSERS_PROTOCOLS.get(Browsers.FIREFOX.name), Constants.BROWSERS_CIPHERS.get(Browsers.FIREFOX.name));
                        Utilities.updateProxySettingsSync(MatchAndReplace.create(Browsers.FIREFOX));
                    }
                }
            });
        }

}
