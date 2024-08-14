import com.akamai.edgegrid.signer.ClientCredential;
import com.akamai.edgegrid.signer.apachehttpclient.ApacheHttpClientEdgeGridInterceptor;
import com.akamai.edgegrid.signer.apachehttpclient.ApacheHttpClientEdgeGridRoutePlanner;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader; 
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
   Purge some urls from akamai via the fast purge api. See the readme for details
 */
public class Purge {
    public static void main(String args[]) throws Exception {
        Purge purge = new Purge();
        purge.go((new DefaultParser()).parse(getOptions(), args));
    }

    private void go(CommandLine cmd) throws Exception {
        String type = cmd.getOptionValue("type");
        if (type == null) {
            type = "invalidate";
        } else if (!type.equals("delete") && !type.equals("invalidate")) {
            throw new IllegalArgumentException("type parameter must be either 'invalidate' or 'delete'");
        }

        String url = "https://" + cmd.getOptionValue("host") +  "/ccu/v3/" + type + "/url/production";
        HttpPost request = new HttpPost(url);
        request.setEntity(new StringEntity(getEntityBody(cmd), ContentType.create("application/json")));

        HttpResponse response = getClient(cmd).execute(request);
        if (response.getStatusLine().getStatusCode()!=201) {
            dumpError(response);
            System.exit(1);
        }
        System.exit(0);
    }

    private void dumpError(HttpResponse response) throws Exception {
        InputStreamReader is =new InputStreamReader(response.getEntity().getContent());
        char buffer[] = new char[4096];
        StringBuilder s = new StringBuilder();
        int i;
        while ((i=is.read(buffer))>0) {
            s.append(buffer,0, i);
        }
        is.close();

        System.out.println("Error: " + response.getStatusLine().getStatusCode());
        System.out.println("body: " + s.toString());
    }

    private static Options getOptions() {
        Options options = new Options();
        options.addRequiredOption("a", "access-token", true, "Access Token");
        options.addRequiredOption("c", "client-token", true, "Client Token");
        options.addRequiredOption("s", "client-secret", true, "Client Secret");
        options.addRequiredOption("h", "host", true, "Host");
        options.addOption("u", "url", true, "Url");
        options.addOption("f", "url-file", true, "Url File");
        options.addOption("t", "type", true, "invalidate or delete - Default is invalidate");

        return options;
    }

    private HttpClient getClient (CommandLine cmd) {
        ClientCredential credential = ClientCredential.builder()
                .accessToken(cmd.getOptionValue("access-token"))
                .clientToken(cmd.getOptionValue("client-token"))
                .clientSecret(cmd.getOptionValue("client-secret"))
                .host(cmd.getOptionValue("host"))
                .build();
        return HttpClientBuilder.create()
                .addInterceptorFirst(new ApacheHttpClientEdgeGridInterceptor(credential))
                .setRoutePlanner(new ApacheHttpClientEdgeGridRoutePlanner(credential))
                .build();
    }

    private String getEntityBody(CommandLine cmd) throws Exception {
        ObjectMapper om = new ObjectMapper();
        om.configure(SerializationFeature.INDENT_OUTPUT, false);
        om.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);

        List<String> urls = new ArrayList<>();
        if (cmd.hasOption("url")) {
            Collections.addAll(urls, cmd.getOptionValues("url"));
        }

        if (cmd.hasOption("url-file")) {
            for (String file:cmd.getOptionValues("url-file")) {
                try (BufferedReader bufferedReader = new BufferedReader(new FileReader(new File(file)))) {
                    String url;
                    while ((url=bufferedReader.readLine())!=null) {
                        url = url.trim();
                        if (url.startsWith("http")) {
                            urls.add(url);
                        }
                    }
                }
            }
        }

        if (urls.isEmpty()) {
            throw new IllegalArgumentException("No urls are defined");
        }

        Map<String, Object> map = new HashMap<>();
        map.put("objects", urls);
        return om.writeValueAsString(map);
    }
 
}
