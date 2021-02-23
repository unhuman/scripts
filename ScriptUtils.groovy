@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.2' )
import groovyx.net.http.HttpResponseException
import groovyx.net.http.RESTClient

// To set up this script in Jenkins
// Parameters:
//   Choice: Couchbase_Environment Values: Development, Alpha, CI, Staging, Production, DR_Only,
//           DR-Test,
//           E-Commerce_Development, E-Commerce_Production, E-Commerce_DR_Only
//   String: Bucket_Name
//   Choice: Design_Document_Level Values : Production, Development
//   Boolean: Version_Check (should be defaulted on)
//
// Restrict where this project can be run: linux
//
// For "Local" setups custom admin and password can be specified using __LOCAL_COUCHBASE_ADMINISTRATOR__ and __LOCAL_COUCHBASE_PASSWORD__
// environement variables.

abstract class ScriptUtils {
    // Prompt for a value - use force_value to skip the prompt & force the value (still do validation).
    // Use case for that is command line parameter
    static def Prompt(question, validation, force_value=null)
    {
        return PromptDefault(question, validation, null, force_value)
    }

    // Prompt for a value - use force_value to skip the prompt & force the value (still do validation).
    // Use case for that is command line parameter
    // This allows specification of a default value
    static String PromptDefault(question, validation, defaultValue, force_value=null)
    {
        // check force_value if provided (for whatever reason, we get False when args don't exist)
        if ((force_value != null) && force_value) {
            def matcher = (force_value =~ validation);
            if (matcher.matches()) {
                println "${question} ${force_value}"
                return force_value
            } else {
                throw new RuntimeException("Invalid value: " + force_value.toString())
            }
        }

        def reader = System.in.newReader()
        def value
        while ({
            print "${question} "
            value = reader.readLine()
            if ((value == '') && (defaultValue != null)) {
                value = defaultValue
            }
            def matcher = (value =~ validation);
            (!matcher.matches())
        }()); // don't delete the semicolon

        return value
    }

    /**
     * Gets a file
     * @param user
     * @param password
     * @param request_url
     * @return
     */
    static def GetFile(String user, String password, String request_url) {
        def url = new URL(request_url)
        HttpURLConnection connection = url.openConnection()
        connection.addRequestProperty('Authorization', 'Basic ' + "${user}:${password}".bytes.encodeBase64())
        connection.requestMethod = 'GET'
        connection.connect()
        switch (connection.responseCode) {
            case 200:
                if (connection.contentType == null) {
                    System.err.println("No content type ${connection.contentType} for ${request_url}")
                    return null
                }
                if (connection.contentType.contains("text/plain")) {
                    connection.getResponseMessage();
                    return connection.getInputStream().getText();
                } else {
                    System.err.println("Unexpected content type ${connection.contentType} for ${request_url}")
                    return null
                }
            case 404:
                return null;
            default:
                throw new Exception("Unknown status code: ${connection.responseCode}")
        }
    }


    /**
     * Runs a REST request
     * @param method
     * @param user
     * @param password
     * @param request_url
     * @param allowed_codes
     * @param contentKvOrJson - (POST) either webform (key=value) or JSON request
     * @return
     */
    static def RunJsonRequest(String method, String user, String password, String request_url,
                              allowed_codes = [200, 201], String contentKvOrJson = null) {
        def client = new RESTClient(request_url)

        // This is not respected - have to specify the authorization header explicitly
        // client.setAuthConfig(new AuthConfig(client).basic(user, password))
        // client.auth.basic(user, password)
        client.headers['Authorization'] = 'Basic ' + "${user}:${password}".bytes.encodeBase64()

        method = method.toUpperCase()
        def response

        try {
            switch (method) {
                case "GET":
                    response = client.get(Collections.emptyMap())
                    break;
                case "POST":
                    Map<String, ?> content = new HashMap<>()
                    content.put("contentType", "application/json")
                    if (contentKvOrJson != null) {
                        // do the correct thing - figure out if we're JSON else KeyValue
                        if (contentKvOrJson.startsWith("{") && contentKvOrJson.endsWith("}")) {
                            // json
                            content.put("requestContentType", "application/json")
                            content.put("body", contentKvOrJson)
                        } else {
                            // Assume webform
                            content.put("requestContentType", "application/x-www-form-urlencoded")
                            // expected post content to be x=y and URL Encode the content
                            String bodyContent = ""
                            String[] items = contentKvOrJson.split("&")
                            for (int i = 0; i < items.length; i++) {
                                String[] contentStrings = items[i].split("=", 2)
                                if (contentStrings.length != 2) {
                                    throw new RuntimeException("Expected key=value data")
                                }
                                bodyContent += (bodyContent.length() != 0) ? "&" : ""
                                bodyContent += "${contentStrings[0]}=${URLEncoder.encode(contentStrings[1], "UTF-8")}"
                            }
                            content.put("body", bodyContent)
                        }
                    }
                    response = client.post(content)
                    break;
                default:
                    throw new RuntimeException("Do not handle method: ${method}")
            }
        } catch (HttpResponseException hre) {
            // details of what went wrong are in here, so log it and re-throw
            println("REST ERROR: ${hre.response.responseData}")
            throw hre
        }

        if (response.status in allowed_codes) {
            return response.getData()
        } else {
            throw new RuntimeException("ERROR: ${response.status} Text: ${response.getData()}")
        }
    }
}
