package banshee.net;

import java.net.Socket;
import java.util.concurrent.Semaphore;
import java.io.*;
import java.util.HashMap;
import java.net.*;
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
public class Connection extends Thread {
    private final Socket Sock;
    private static final File CACHE_FOLDER = new File("./cache/");
    private Socket Con;
    private final Semaphore Sem;
    private final HashMap<String, String> AddrMap;
    private final HashMap<String, String> TitleMap;
    private final HashMap<File, Integer> StateMap;
    private final ConnectionInformation ConInfo = new ConnectionInformation();
    private final String Proxy;
    private final int ProxyPort;
    private final Pattern BannedPattern;
    //データ専用　どちらもビッグエンディアンなので楽。
    private DataInputStream DataIn;
    private DataOutputStream DataOut;
    //テキスト専用
    private BufferedReader TextIn;
    private BufferedWriter TextOut;
    //データ専用　どちらもビッグエンディアンなので楽。
    private DataInputStream ConDataIn;
    private DataOutputStream ConDataOut;
    //テキスト専用
    private BufferedReader ConTextIn;
    private BufferedWriter ConTextOut;
    public Connection(final Socket sock,
                      final Semaphore sem,
                      final HashMap<String, String> map,
                      final HashMap<String, String> title_map,
                      final HashMap<File, Integer> set,
                      final String proxy, final int port,
                      final Pattern pattern) {
        Sock = sock;
        Sem = sem;
        AddrMap = map;
        TitleMap = title_map;
        StateMap = set;
        Proxy = proxy;
        ProxyPort = port;
        BannedPattern = pattern;
    }

    private byte Buff[] = new byte[(1024 * 512)];
    public void run() {
        try {
            exec();
        } finally {
            Sem.release();
        }
    }

    public void exec() {
        try {
            if (Sock == null ||
                Sock.isClosed() ||
                !Sock.isConnected() ||
                !initStream()) {
                return;
            }
            /*リクエスト・ヘッダ*/
            //最初の一行を取得して、接続先を確定させる
            String str = TextIn.readLine();
            if(str == null){
                return;
            }
            if (BannedPattern != null && BannedPattern.matcher(str).matches()) {
                return;
            }
            ConInfo.parseInfo(str);
            /*接続設定の振り分け*/
            if (ConInfo.Method.equals("CONNECT")) {
                execTonnerConnection();
            } else {
                final String video_id = AddrMap.get(ConInfo.URL);
                final String video_title = TitleMap.get(video_id);
                if (video_id == null) { /*無いなら*/
                    execNormalConnection(null, null, false);
                } else {
                    File local = this.findCache(video_id, false);
                    File local_tmp = this.findCache(video_id, true);
                    if (local_tmp != null && StateMap.containsKey(local_tmp)) {
                        /*続き*/
                        execLocalConnection(local_tmp, true);
                    } else if (local != null) {
                        /*ダウンロード済み*/
                        execLocalConnection(local, false);
                    } else if (local_tmp != null) {
                        String tmp = video_id + "_" + video_title +
                                     ".flv";
                        local = new File(CACHE_FOLDER, tmp);
                        /*テンポラリなら、ある。*/
                        execNormalConnection(local, local_tmp, true);

                    } else {
                        /*ダウンロードこれから*/
                        String tmp = video_id + "_" + video_title +
                                     ".flv";
                        local = new File(CACHE_FOLDER, tmp);
                        local_tmp = new File(CACHE_FOLDER, "tmp_" + tmp);
                        execNormalConnection(local, local_tmp, false);
                    }
                }
            }
            /*おわり*/
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            try {
                DataIn.close();
            } catch (IOException ex1) {
            }
            try {
                DataOut.close();
            } catch (IOException ex2) {
            }
            try {
                TextIn.close();
            } catch (IOException ex3) {
            }
            try {
                TextOut.close();
            } catch (IOException ex4) {
            }
            try {
                Sock.close();
            } catch (IOException ex5) {
            }
        }
    }

    private File findCache(final String video_id, final boolean isTmp) {
        String[] list = (new File("./cache/").list());
        final String pattern;
        if (isTmp) {
            pattern = "tmp_" + video_id;
        } else {
            pattern = video_id;
        }
        for (int i = 0; i < list.length; i++) {
            if (list[i].startsWith(pattern)) {
                return new File(CACHE_FOLDER, list[i]);
            }
        }
        return null;
    }

    public boolean initStream() {
        try {
            InputStream in = Sock.getInputStream();
            OutputStream os = Sock.getOutputStream();
            TextIn = new BufferedReader(new InputStreamReader(new
                    HTTP_SocketStreamReader(in)));
            TextOut = new BufferedWriter(new OutputStreamWriter(os));
            DataIn = new DataInputStream(in);
            DataOut = new DataOutputStream(os);
        } catch (IOException ex) {
            ex.printStackTrace();
            return false;
        }
        return true;
    }

    /*普通の接続*/
    private final String VIDEO_INFO_URL_PARSER =
            "http://www.nicovideo.jp/api/getflv?v=";
    private final String VIDEO_TITLE_URL_PARSER =
            "http://www.nicovideo.jp/watch/";
    private void execNormalConnection(final File file, final File tmp_file,
                                      final boolean is_tmp) {
        final boolean file_exist = (file != null && file.exists());
        final boolean tmp_exist = (tmp_file != null && tmp_file.exists());
        try {
            /*ファイルロック*/
            if (!file_exist) {
                StateMap.put(tmp_file, new Integer( -1));
            }
            int idx;
            /*ビデオ情報*/
            boolean is_video_info = false;
            boolean is_video_title = false;
            String video_id = null;
            if ((idx = ConInfo.URL.indexOf(VIDEO_INFO_URL_PARSER)) >= 0) {
                is_video_info = true;
                video_id = ConInfo.URL.substring(idx +
                                                 VIDEO_INFO_URL_PARSER.length());
            } else if ((idx = ConInfo.URL.indexOf(VIDEO_TITLE_URL_PARSER)) >= 0) {
                is_video_title = true;
                video_id = ConInfo.URL.substring(idx +
                                                 VIDEO_TITLE_URL_PARSER.length());
            }
            /*接続開始*/
            if (ProxyPort < 0) {
                Con = new Socket(ConInfo.Server, ConInfo.Port);
            } else {
                Con = new Socket(Proxy, ProxyPort);
            }
            if (Con == null || !initConStream()) {
                System.out.println(ConInfo.Server + ConInfo.Request +
                                   " : connection error");
                return;
            }
            if (ProxyPort < 0) {
                ConTextOut.write(ConInfo.Method + " " + ConInfo.Request +
                                 " HTTP/1.1\r\n");
            } else {
                ConTextOut.write(ConInfo.Method + " " + ConInfo.URL +
                                 " HTTP/1.1\r\n");
            }
            String str;
            int content_length = -1;
            while ((str = TextIn.readLine()) != null && str.length() > 0) {
                //プロキシであることをかくす。
                if (ProxyPort < 0) {
                    str = str.replaceAll("Proxy-", "");
                    str = str.replaceAll("proxy-", "");
                }
                /*その他*/
                String str_lower = str.toLowerCase();
                if ((idx = str_lower.indexOf("content-length: ")) >= 0) {
                    content_length = Integer.parseInt
                                     (str_lower.substring
                                      (idx + "content-length: ".length()));
                } else if (str_lower.startsWith("connection:")) {
                    str = "Connection: close";
                } else if (str_lower.startsWith("keep-alive:") ||
                           str_lower.indexOf("range") >= 0 ||
                           str_lower.indexOf("unless-modified-since") >= 0) {
                    continue;
                }
                ConTextOut.write(str + "\r\n");
            }
            if (tmp_exist && is_tmp) { //テンポラリ
                ConTextOut.write("Range: bytes=" +
                                 Long.toString(tmp_file.length()) + "-\r\n");
            }
            ConTextOut.write("\r\n");
            ConTextOut.flush();
            /*リクエスト・データ*/
            if (content_length >= 0) {
                int sent_size = 0;
                int size = 0;
                while (sent_size < content_length &&
                       (size = DataIn.read(Buff)) > 0) {
                    sent_size += size;
                    ConDataOut.write(Buff, 0, size);
                }
                ConDataOut.flush();
            }
            /*リザルト・ヘッダ*/
            str = ConTextIn.readLine();
            if(str == null){//どう見てもエラーですよね？
                TextOut.write("HTTP/1.1 504 Gateway Timeout\r\n");
                TextOut.write("Connection: close\r\n");
                TextOut.write("Server: Banshee!\r\n");
                TextOut.write("Content-Length: 0!\r\n");
                TextOut.write("\r\n");
                TextOut.flush();
                return;
            }
            
            boolean isPart = false;
            if (str.indexOf(" 206 ") > 0) {
                isPart = true;
            }
            idx = str.indexOf("HTTP/1.1 ");
            String code = str.substring(idx+9);
            content_length = -1;
            if(isPart){
            	TextOut.write("HTTP/1.1 200 OK\r\n");
            }else{
            	TextOut.write("HTTP/1.1 "+code+"\r\n");
            }
            while ((str = ConTextIn.readLine()) != null && str.length() > 0) {
                String str_lower = str.toLowerCase();
                if (content_length < 0 &&
                    (idx = str_lower.indexOf("content-length: ")) >= 0) {
                    content_length = Integer.parseInt
                                     (str_lower.substring
                                      (idx + "content-length: ".length()));
                }
                TextOut.write(str + "\r\n"); //特に書き換えたりは・・・。
            }
            TextOut.write("\r\n");
            TextOut.flush();
            /*リザルト・データ*/
            int size = 0;
            int left = content_length;
            if (is_video_info || is_video_title) {
                /*ハッシュ登録用*/
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                while (left != 0 && (size = ConDataIn.read(Buff)) > 0) {
                    baos.write(Buff, 0, size);
                    left -= size;
                }
                byte data[] = baos.toByteArray();
                DataOut.write(data);
                DataOut.flush();
                if (is_video_info) {
                    //URLハッシュ登録作業
                    str = baos.toString();
                    idx = str.indexOf("url=") + 4;
                    String video_url = str.substring(idx, str.indexOf("&", idx));
                    video_url = URLDecoder.decode(video_url, "US-ASCII");
                    AddrMap.put(video_url, video_id);
                } else if (is_video_title) {
                    //タイトルハッシュ登録作業
                    str = baos.toString("UTF-8");
                    idx = str.indexOf("<title>");
                    idx = str.indexOf("‐", idx);
                    String video_title = str.substring
                                         (idx + 1, str.indexOf("</title>"));

                    TitleMap.put(video_id, safeFileName(video_title));
                }
            } else if (file != null) {
                /*動画DL用*/
                boolean is_connected = true;
                try {
                    Integer total_size;
                    if (isPart) {
                        total_size = new Integer((int) tmp_file.length() +
                                                 content_length);
                        System.out.println("Resume cache:" + tmp_file.getName());
                    } else {
                        total_size = new Integer(content_length);
                        System.out.println("Downloading:" + tmp_file.getName());
                    }
                    StateMap.put(tmp_file, total_size);
                    DataOutputStream dos = new DataOutputStream
                                           (new FileOutputStream(tmp_file,
                            isPart));
                    DataInputStream dis = new DataInputStream
                                          (new FileInputStream(tmp_file));
                    boolean file_end = false;
                    while ((left != 0 && (size = ConDataIn.read(Buff)) > 0) ||
                                                 !(file_end || !is_connected)) {
                        dos.write(Buff, 0, size);
                        left -= size;
                        if (is_connected) {
                            try {
                                size = dis.read
                                       (Buff, 0,
                                        Math.min(Buff.length, dis.available()));
                                DataOut.write(Buff, 0, size);
                            } catch (Exception ex) {
                                System.out.println
                                        ("Connection Aborted:" +
                                         tmp_file.getName());
                                is_connected = false;
                            }
                        }
                        file_end = dis.available() <= 0;
                    }
                    dis.close();
                    DataOut.flush();
                    dos.flush();
                    dos.close();
                    if (left == 0) {
                        tmp_file.renameTo(file);
                        System.out.println("Completed:" + file.getName());
                    }
                } catch (Exception ex1) {
                    file.delete();
                }
            } else {
                /*通常*/
                while (left != 0 && (size = ConDataIn.read(Buff)) > 0) {
                    DataOut.write(Buff, 0, size);
                    left -= size;
                }
                DataOut.flush();
            }
        } catch (NumberFormatException ex) {
            ex.printStackTrace();
        } catch (UnknownHostException ex) {
            ex.printStackTrace();
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            if (tmp_file != null) {
                StateMap.remove(tmp_file);
            }
            try {
                ConDataIn.close();
            } catch (IOException ex6) {
            }
            try {
                ConDataOut.close();
            } catch (IOException ex7) {
            }
            try {
                ConTextIn.close();
            } catch (IOException ex8) {
            }
            try {
                ConTextOut.close();
            } catch (IOException ex9) {
            }
            try {
                Con.close();
            } catch (IOException ex10) {
            }
        }
    }

    private static String safeFileName(String str) {
        str = str.replace('/', '／');
        str = str.replace('\\', '￥');
        str = str.replace('?', '？');
        str = str.replace('*', '＊');
        str = str.replace(':', '：');
        str = str.replace('|', '｜');
        str = str.replace('\"', '”');
        str = str.replace('<', '＜');
        str = str.replace('>', '＞');
        str = str.replace('.', '．');
        return str;
    }

    private void execTonnerConnection() {
        String str;
        try {
            while ((str = TextIn.readLine()) != null && str.length() > 0) {
            }
            /*接続開始*/
            if (ProxyPort < 0) {
                Con = new Socket(ConInfo.Server, ConInfo.Port);
            } else {
                Con = new Socket(Proxy, ProxyPort);
            }
            if (Con == null || !initConStream()) {
                System.out.println(ConInfo.Server + ConInfo.Request +
                                   " : connection error");
                return;
            }
            if (ProxyPort >= 0) {
                ConTextOut.write("CONNECT " + ConInfo.URL + " HTTP/1.1");
                ConTextOut.flush();
            }

            /*返送*/
            TextOut.write("HTTP/1.1 200 Connection established\r\n");
            TextOut.write("Connection: close\r\n");
            TextOut.write("\r\n");
            TextOut.flush();
            Thread in = new TonnerSend(ConDataIn, DataOut);
            Thread out = new TonnerSend(DataIn, ConDataOut);
            in.start();
            out.start();
            in.join();
            out.join();
        } catch (IOException ex) {
            ex.printStackTrace();
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        } finally {
            try {
                ConDataIn.close();
            } catch (IOException ex6) {
            }
            try {
                ConDataOut.close();
            } catch (IOException ex7) {
            }
            try {
                ConTextIn.close();
            } catch (IOException ex8) {
            }
            try {
                ConTextOut.close();
            } catch (IOException ex9) {
            }
            try {
                Con.close();
            } catch (IOException ex10) {
            }
        }
    }

    public boolean initConStream() {
        try {
            InputStream in = Con.getInputStream();
            OutputStream os = Con.getOutputStream();
            ConTextIn = new BufferedReader(new InputStreamReader(new
                    HTTP_SocketStreamReader(in)));
            ConTextOut = new BufferedWriter(new OutputStreamWriter(os));
            ConDataIn = new DataInputStream(in);
            ConDataOut = new DataOutputStream(os);
        } catch (IOException ex) {
            ex.printStackTrace();
            return false;
        }
        return true;
    }

    private void execLocalConnection(final File local, boolean is_tmp) {
        try {
            /*まずはデータを全部送信し終わるまで待つ。*/
            String str;
            while ((str = TextIn.readLine()) != null && str.length() > 0) {
            }
            System.out.println("Read from cache:" + local.getName());
            /*送信*/
            long size;
            if (is_tmp) {
                while ((size = StateMap.get(local).intValue()) < 0) {
                    sleepA(100);
                }
            } else {
                size = local.length();
            }
            TextOut.write("HTTP/1.1 200 OK\r\n");
            TextOut.write("Content-Type: video/flv\r\n");
            TextOut.write("Server: Banshee!\r\n");
            TextOut.write("Content-Length: " + Long.toString(size) + "\r\n");
            TextOut.write("Connection: close\r\n\r\n");
            TextOut.flush();
            InputStream is = new FileInputStream(local);
            int read = 0;
            long left = size;
            while ((is_tmp && left > 0 && StateMap.containsKey(local)) ||
                   is.available() > 0) {
                read = is.read(Buff, 0, Math.min(Buff.length, is.available()));
                left -= read;
                if (read > 0 && read <= Buff.length) {
                    DataOut.write(Buff, 0, read);
                } else {
                    sleepA(100);
                }
            }
            DataOut.flush();
            is.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void sleepA(long msec) {
        try {
            sleep(msec);
        } catch (InterruptedException e) {}

    }
}


class TonnerSend extends Thread {
    private final InputStream IS;
    private final OutputStream OS;
    private final byte[] Buff = new byte[1024];
    public TonnerSend(InputStream is, OutputStream os) {
        IS = is;
        OS = os;
    }

    public void run() {
        try {
            int size;
            while ((size = IS.read(Buff)) > 0) {
                OS.write(Buff, 0, size);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
