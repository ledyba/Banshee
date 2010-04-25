package banshee;

import banshee.net.NicoProxy;
import java.util.Properties;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.*;
import java.util.regex.Pattern;

/**
 * <p>タイトル: ニコニコ動画キャッシュプロキシ「ばんしー！」</p>
 *
 * <p>説明: アリアかわいいよアリア</p>
 *
 * <p>著作権: Copyright (c) 2007 PSI</p>
 *
 * <p>会社名: </p>
 *
 * @author 未入力
 * @version 1.0
 */
public class Prompt {
    public static void main(String[] args) {
        System.out.println("-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-");
        System.out.println("ニコニコ動画キャッシュプロキシ「ばんしー！」");
        System.out.println("ver1.01 2007/12/31 written by PSI");
        System.out.println("blog:http://ledyba.ddo.jp/");
        System.out.println("-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-");
        int port = 8080;
        String proxy = null;
        int proxy_port = -1;
        String filter = null;
        Pattern pat = null;
        try {
            final Properties prop = new Properties();
            final InputStream is = new FileInputStream("Banshee!.conf");
            prop.load(is);
            is.close();
            port = Integer.parseInt(prop.getProperty("ListenPort", "8080"));
            if (prop.getProperty("UsingProxy", "false")
                .toLowerCase().equals("true")) {
                proxy = prop.getProperty("Proxy", null);
                proxy_port = Integer.getInteger(prop.getProperty("ProxyPort",
                        "-1"));
            }
            boolean enable_filter =
                    prop.getProperty("EnableFilter", "false")
                    .toLowerCase().equals("true");
            if (enable_filter) {
                filter = prop.getProperty("Filter", "");
                pat = Pattern.compile(".*(" + filter.replace(' ', '|') + ")+.*");
            }
        } catch (FileNotFoundException ex) {
            System.out.println("Config file was not found.");
        } catch (IOException ex) {
            System.out.println("IO Error while reading config.");
        }
        System.out.println("ListenPort: " + port);
        if (proxy_port < 0) {
            System.out.println("Proxy: disabled");
        } else {
            System.out.println("Proxy: " + proxy + ":" + proxy_port);
        }
        if (pat != null) {
            System.out.println("Filter: " + filter);
        } else {
            System.out.println("Filter: disabled");
        }
        System.out.println("-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-");
        NicoProxy nico_proxy = new NicoProxy(port, proxy, proxy_port, pat);
        nico_proxy.start();
    }
}
