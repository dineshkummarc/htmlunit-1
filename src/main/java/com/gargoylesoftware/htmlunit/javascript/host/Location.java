/*
 * Copyright (c) 2002-2008 Gargoyle Software Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gargoylesoftware.htmlunit.javascript.host;

import static com.gargoylesoftware.htmlunit.protocol.javascript.JavaScriptURLConnection.JAVASCRIPT_PREFIX;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.util.URIUtil;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequestSettings;
import com.gargoylesoftware.htmlunit.WebWindow;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.javascript.JavaScriptEngine;
import com.gargoylesoftware.htmlunit.javascript.PostponedAction;
import com.gargoylesoftware.htmlunit.javascript.SimpleScriptable;
import com.gargoylesoftware.htmlunit.util.UrlUtils;

/**
 * A JavaScript object for a Location.
 *
 * @version $Revision: 3176 $
 * @author <a href="mailto:mbowler@GargoyleSoftware.com">Mike Bowler</a>
 * @author Michael Ottati
 * @author Marc Guillemot
 * @author Chris Erskine
 * @author Daniel Gredler
 * @author David K. Taylor
 * @author Ahmed Ashour
 * @see <a href="http://msdn.microsoft.com/workshop/author/dhtml/reference/objects/obj_location.asp">
 * MSDN Documentation</a>
 */
public class Location extends SimpleScriptable {

    private static final long serialVersionUID = -2907220432378132233L;
    private static final String UNKNOWN = "null";

    /**
     * The window which owns this location object.
     */
    private Window window_;

    /**
     * The current hash; we cache it locally because we don't want to modify the page's URL and
     * force a page reload each time this changes.
     */
    private String hash_;

    /**
     * Creates an instance. JavaScript objects must have a default constructor.
     */
    public Location() {
        // Empty.
    }

    /**
     * Initializes the object.
     *
     * @param window the window that this location belongs to
     */
    public void initialize(final Window window) {
        window_ = window;
        if (window_ != null && window_.getWebWindow().getEnclosedPage() != null) {
            hash_ = window_.getWebWindow().getEnclosedPage().getWebResponse().getUrl().getRef();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public Object getDefaultValue(final Class hint) {
        if (hint == null || String.class.equals(hint)) {
            return jsxGet_href();
        }
        return super.getDefaultValue(hint);
    }

    /**
     * Returns the string value of the location, which is the full URL string.
     * @return the full URL string
     */
    @Override
    public String toString() {
        if (window_ != null) {
            return jsxGet_href();
        }
        return "[Uninitialized]";
    }

    /**
     * Loads the new HTML document corresponding to the specified URL.
     * @param url the location of the new HTML document to load
     * @throws IOException if loading the specified location fails
     * @see <a href="http://msdn.microsoft.com/workshop/author/dhtml/reference/methods/assign.asp">
     * MSDN Documentation</a>
     */
    public void jsxFunction_assign(final String url) throws IOException {
        jsxSet_href(url);
    }

    /**
     * Reloads the current page, possibly forcing retrieval from the server even if
     * the browser cache contains the latest version of the document.
     * @param force If <tt>true</tt>, force reload from server; otherwise, may reload from cache
     * @throws IOException if there is a problem reloading the page
     * @see <a href="http://msdn.microsoft.com/workshop/author/dhtml/reference/methods/reload.asp">
     * MSDN Documentation</a>
     */
    public void jsxFunction_reload(final boolean force) throws IOException {
        final String url = jsxGet_href();
        if (UNKNOWN.equals(url)) {
            getLog().error("Unable to reload location: current URL is unknown.");
        }
        else {
            jsxSet_href(url);
        }
    }

    /**
     * Reloads the window using the specified URL.
     * @param url the new URL to use to reload the window
     * @throws IOException if there is a problem loading the new page
     * @see <a href="http://msdn.microsoft.com/workshop/author/dhtml/reference/methods/replace.asp">
     * MSDN Documentation</a>
     */
    public void jsxFunction_replace(final String url) throws IOException {
        final JavaScriptEngine jsEngine = getWindow().getWebWindow().getWebClient().getJavaScriptEngine();
        final PostponedAction action = new PostponedAction() {
            public void execute() throws Exception {
                jsxSet_href(url);
            }
        };
        jsEngine.addPostponedAction(action);
    }

    /**
     * Returns the location URL.
     * @return the location URL
     */
    public String jsxFunction_toString() {
        return jsxGet_href();
    }

    /**
     * Returns the location URL.
     * @return the location URL
     * @see <a href="http://msdn.microsoft.com/workshop/author/dhtml/reference/properties/href_3.asp">
     * MSDN Documentation</a>
     */
    public String jsxGet_href() {
        final Page page = window_.getWebWindow().getEnclosedPage();
        if (page == null) {
            return UNKNOWN;
        }
        try {
            URL url = page.getWebResponse().getUrl();
            final boolean encodeHash = !getBrowserVersion().isIE();
            final String hash = getHash(encodeHash);
            if (hash != null) {
                url = UrlUtils.getUrlWithNewRef(url, hash);
            }
            return url.toExternalForm();
        }
        catch (final MalformedURLException e) {
            getLog().error(e.getMessage(), e);
            return page.getWebResponse().getUrl().toExternalForm();
        }
    }

    /**
     * Sets the location URL to an entirely new value.
     * @param newLocation the new location URL
     * @throws IOException if loading the specified location fails
     * @see <a href="http://msdn.microsoft.com/workshop/author/dhtml/reference/properties/href_3.asp">
     * MSDN Documentation</a>
     */
    public void jsxSet_href(final String newLocation) throws IOException {
        // URL should be resolved from the page in which the js is executed
        // cf test FrameTest#testLocation
        final HtmlPage page = (HtmlPage) getWindow(getStartingScope()).getWebWindow().getEnclosedPage();

        if (newLocation.startsWith(JAVASCRIPT_PREFIX)) {
            final String script = newLocation.substring(11);
            page.executeJavaScriptIfPossible(script, "new location value", 1);
        }
        else {
            try {
                final URL url = page.getFullyQualifiedUrl(newLocation);

                final WebWindow webWindow = getWindow().getWebWindow();
                webWindow.getWebClient().getPage(webWindow, new WebRequestSettings(url));
            }
            catch (final MalformedURLException e) {
                getLog().error("jsxSet_location(\"" + newLocation + "\") Got MalformedURLException", e);
                throw e;
            }
            catch (final IOException e) {
                getLog().error("jsxSet_location(\"" + newLocation + "\") Got IOException", e);
                throw e;
            }
        }
    }

    /**
     * Returns the search portion of the location URL (the portion following the '?').
     * @return the search portion of the location URL
     * @see <a href="http://msdn.microsoft.com/workshop/author/dhtml/reference/properties/search.asp">
     * MSDN Documentation</a>
     */
    public String jsxGet_search() {
        final String search = getUrl().getQuery();
        if (search == null) {
            return "";
        }
        return "?" + search;
    }

    /**
     * Sets the search portion of the location URL (the portion following the '?').
     * @param search the new search portion of the location URL
     * @throws Exception if an error occurs
     * @see <a href="http://msdn.microsoft.com/workshop/author/dhtml/reference/properties/search.asp">
     * MSDN Documentation</a>
     */
    public void jsxSet_search(final String search) throws Exception {
        setUrl(UrlUtils.getUrlWithNewQuery(getUrl(), search));
    }

    /**
     * Returns the hash portion of the location URL (the portion following the '#').
     * @return the hash portion of the location URL
     * @see <a href="http://msdn.microsoft.com/workshop/author/dhtml/reference/properties/hash.asp">
     * MSDN Documentation</a>
     */
    public String jsxGet_hash() {
        final String hash = getHash(false);
        if (hash != null) {
            return "#" + hash;
        }
        return "";
    }

    private String getHash(final boolean encoded) {
        if (hash_ == null || hash_.length() == 0) {
            return null;
        }

        if (encoded) {
            try {
                return URIUtil.encodeQuery(hash_);
            }
            catch (final URIException e) {
                getLog().error(e.getMessage(), e);
            }
        }
        return hash_;
    }

    /**
     * Sets the hash portion of the location URL (the portion following the '#').
     *
     * @param hash the new hash portion of the location URL
     * @see <a href="http://msdn.microsoft.com/workshop/author/dhtml/reference/properties/hash.asp">MSDN Docs</a>
     */
    public void jsxSet_hash(String hash) {
        // IMPORTANT: This method must not call setUrl(), because
        // we must not hit the server just to change the hash!
        try {
            if (hash != null) {
                if (hash.startsWith("#")) {
                    hash = hash.substring(1);
                }
                final boolean decodeHash = !getBrowserVersion().isIE();
                if (decodeHash) {
                    hash = URIUtil.decode(hash);
                }
                hash_ = hash;
            }
            else {
                hash_ = null;
            }
        }
        catch (final URIException e) {
            getLog().error(e.getMessage(), e);
        }
    }

    /**
     * Returns the hostname portion of the location URL.
     * @return the hostname portion of the location URL
     * @see <a href="http://msdn.microsoft.com/workshop/author/dhtml/reference/properties/hostname.asp">
     * MSDN Documentation</a>
     */
    public String jsxGet_hostname() {
        return getUrl().getHost();
    }

    /**
     * Sets the hostname portion of the location URL.
     * @param hostname the new hostname portion of the location URL
     * @throws Exception if an error occurs
     * @see <a href="http://msdn.microsoft.com/workshop/author/dhtml/reference/properties/hostname.asp">
     * MSDN Documentation</a>
     */
    public void jsxSet_hostname(final String hostname) throws Exception {
        setUrl(UrlUtils.getUrlWithNewHost(getUrl(), hostname));
    }

    /**
     * Returns the host portion of the location URL (the '[hostname]:[port]' portion).
     * @return the host portion of the location URL
     * @see <a href="http://msdn.microsoft.com/workshop/author/dhtml/reference/properties/host.asp">
     * MSDN Documentation</a>
     */
    public String jsxGet_host() {
        final URL url = getUrl();
        final int port = url.getPort();
        final String host = url.getHost();

        if (port == -1) {
            return host;
        }
        return host + ":" + port;
    }

    /**
     * Sets the host portion of the location URL (the '[hostname]:[port]' portion).
     * @param host the new host portion of the location URL
     * @throws Exception if an error occurs
     * @see <a href="http://msdn.microsoft.com/workshop/author/dhtml/reference/properties/host.asp">
     * MSDN Documentation</a>
     */
    public void jsxSet_host(final String host) throws Exception {
        final String hostname;
        final int port;
        final int index = host.indexOf(':');
        if (index != -1) {
            hostname = host.substring(0, index);
            port = Integer.parseInt(host.substring(index + 1));
        }
        else {
            hostname = host;
            port = -1;
        }
        final URL url1 = UrlUtils.getUrlWithNewHost(getUrl(), hostname);
        final URL url2 = UrlUtils.getUrlWithNewPort(url1, port);
        setUrl(url2);
    }

    /**
     * Returns the pathname portion of the location URL.
     * @return the pathname portion of the location URL
     * @see <a href="http://msdn.microsoft.com/workshop/author/dhtml/reference/properties/pathname.asp">
     * MSDN Documentation</a>
     */
    public String jsxGet_pathname() {
        return getUrl().getPath();
    }

    /**
     * Sets the pathname portion of the location URL.
     * @param pathname the new pathname portion of the location URL
     * @throws Exception if an error occurs
     * @see <a href="http://msdn.microsoft.com/workshop/author/dhtml/reference/properties/pathname.asp">
     * MSDN Documentation</a>
     */
    public void jsxSet_pathname(final String pathname) throws Exception {
        setUrl(UrlUtils.getUrlWithNewPath(getUrl(), pathname));
    }

    /**
     * Returns the port portion of the location URL.
     * @return the port portion of the location URL
     * @see <a href="http://msdn.microsoft.com/workshop/author/dhtml/reference/properties/port.asp">
     * MSDN Documentation</a>
     */
    public String jsxGet_port() {
        final int port = getUrl().getPort();
        if (port == -1) {
            return "";
        }
        return String.valueOf(port);
    }

    /**
     * Sets the port portion of the location URL.
     * @param port the new port portion of the location URL
     * @throws Exception if an error occurs
     * @see <a href="http://msdn.microsoft.com/workshop/author/dhtml/reference/properties/port.asp">
     * MSDN Documentation</a>
     */
    public void jsxSet_port(final String port) throws Exception {
        setUrl(UrlUtils.getUrlWithNewPort(getUrl(), Integer.parseInt(port)));
    }

    /**
     * Returns the protocol portion of the location URL, including the trailing ':'.
     * @return the protocol portion of the location URL, including the trailing ':'
     * @see <a href="http://msdn.microsoft.com/workshop/author/dhtml/reference/properties/protocol.asp">
     * MSDN Documentation</a>
     */
    public String jsxGet_protocol() {
        return getUrl().getProtocol() + ":";
    }

    /**
     * Sets the protocol portion of the location URL.
     * @param protocol the new protocol portion of the location URL
     * @throws Exception if an error occurs
     * @see <a href="http://msdn.microsoft.com/workshop/author/dhtml/reference/properties/protocol.asp">
     * MSDN Documentation</a>
     */
    public void jsxSet_protocol(final String protocol) throws Exception {
        setUrl(UrlUtils.getUrlWithNewProtocol(getUrl(), protocol));
    }

    /**
     * Returns this location's current URL.
     * @return this location's current URL
     */
    private URL getUrl() {
        return window_.getWebWindow().getEnclosedPage().getWebResponse().getUrl();
    }

    /**
     * Sets this location's URL, triggering a server hit and loading the resultant document
     * into this location's window.
     * @param url This location's new URL
     * @throws IOException if there is a problem loading the new location
     */
    private void setUrl(final URL url) throws IOException {
        window_.getWebWindow().getWebClient().getPage(window_.getWebWindow(), new WebRequestSettings(url));
    }

}