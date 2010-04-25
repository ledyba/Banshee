package banshee.net;

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
public class ConnectionInformation {
    public String Method;
    public String URL;
    public String Server;
    public String Request;
    public int Port;
    public void parseInfo(String req){
        Method = getMethod(req);
        URL = getURL(req);
        int idx = URL.indexOf("://") + 3;
        if(idx < 3){
            idx = 0;
        }
        int end_idx = URL.indexOf("/", idx);
        if(end_idx<0){
            end_idx = URL.length();
        }
        Server = URL.substring(idx, end_idx);
        Request = URL.substring(end_idx);
        Port = 80;
        if ((idx = Server.indexOf(":")) > 0) {
            Port = Integer.parseInt(Server.substring(idx + 1));
            Server = Server.substring(0, idx);
        }
    }
    private static String getMethod(String method) {
        return method.substring(0, method.indexOf(" "));
    }

    private static String getURL(String method) {
        int idx = method.indexOf(" ") + 1;
        return method.substring(idx, method.indexOf(" ", idx));
    }
}
