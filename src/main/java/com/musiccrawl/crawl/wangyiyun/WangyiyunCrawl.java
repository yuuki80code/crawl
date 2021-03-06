package com.musiccrawl.crawl.wangyiyun;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.musiccrawl.crawl.DefaultCrawl;
import com.musiccrawl.common.entity.PlayList;
import com.musiccrawl.common.entity.Song;
import com.musiccrawl.common.myexception.FailedCrawlResultException;
import com.musiccrawl.util.WYYEncryptUtil;
import net.dongliu.requests.Requests;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Created by Administrator on 2017/9/16.
 */
@Component
public class WangyiyunCrawl extends DefaultCrawl {
    // http herader msg
    private static Map<String, String> headers = new HashMap<>();
    private static final String PLAYLISTURL = "http://music.163.com/weapi/v3/playlist/detail";
    private static final String SONGURL = "http://music.163.com/weapi/song/enhance/player/url?csrf_token=";
    private static final String LYRICURL = "http://music.163.com/weapi/song/lyric?csrf_token=";
    private static final String SEARCHURL= "http://music.163.com/api/search/pc";

    static {
        headers.put("Referer", "http://music.163.com");
        headers.put("Origin", "http://music.163.com");
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.71 Safari/537.36");
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        // headers.put("Host","http://music.163.com");
    }


    public Map<String,Object> getPlayList(String url){
        return getPlayList(url,1);
    }
    //获取歌单列表
    public Map<String,Object> getPlayList(String url,int type) throws FailedCrawlResultException{
        Map<String,Object> resultMap = new HashMap<>();
        try {
            String result = getContentByUrl(url);
            Document document = Jsoup.parse(result);
            document.setBaseUri(url);
            Elements elements = document.select(".m-cvrlst li");
            List<PlayList> list = new ArrayList<>();
            for (Element e: elements) {
                PlayList play = new PlayList();
                Element select = e.select(".u-cover .j-flag").first();
                play.setImgUrl(select.attr("src"));
                select = e.select(".u-cover a").first();
                play.setTitle(select.attr("title"));
                play.setUrl(select.absUrl("href"));
                play.setId(select.attr("href").split("=")[1]);
                select = e.select(".u-cover .bottom .nb").first();
                play.setCount(select.text());
                play.setPlatformCode(1);
                play.setType(type);
                list.add(play);
            }
            resultMap.put("playList",list);
            // 抓取下一页地址
            elements = document.select(".u-page .znxt");
            String nextUrl = elements.first().absUrl("href");
            resultMap.put("nextUrl",nextUrl);
            return resultMap;
        } catch (Exception e) {
//            e.printStackTrace();
            throw new FailedCrawlResultException("未抓取到数据");
        }
    }


    public List<Song> getSongList(String url){
        return getSongList(url,1);
    }
    // 获取歌单里的所有歌曲列表
    public List<Song> getSongList(String url,int type){
        List<Song> songs = new ArrayList<>();
        try {
            String id = url.split("=")[1];
            String data = "{\"id\":\""+id+"\",\"offset\":0,\"total\":true,\"limit\":1000,\"n\":1000,\"csrf_token\":\"\"}";
            Map<String, String> forms = WYYEncryptUtil.encrypt(data);
            String text = Requests.post(PLAYLISTURL).headers(headers).forms(forms).send().readToText();
            JSONObject obj = JSONObject.parseObject(text);
            JSONArray tracks = obj.getJSONObject("playlist").getJSONArray("tracks");
            //System.out.println(tracks.size());
            for (int i=0;i<tracks.size();i++){
                JSONObject song = tracks.getJSONObject(i);
                Song s = new Song();
                s.setId(song.getInteger("id")+"");
                s.setName(song.getString("name"));
                s.setImgUrl(song.getJSONObject("al").getString("picUrl"));
                s.setUrl(getSongUrl(s.getId()));
                s.setLrcText(getLyric(s.getId()));
                s.setSinger(getSinger(song.getJSONArray("ar")));
                s.setPlantformCode(1);
                s.setType(type);
                songs.add(s);
            }
        }catch (Exception e){
            e.printStackTrace();
        }


        return songs;
    }
    // 搜索
    public List<Song> serachSong(String songName){
        List<Song> songs = new ArrayList<>();
        Map<String,Object> forms = new HashMap<>();
        forms.put("s",songName);
        forms.put("offset",0);
        forms.put("limit",20);
        forms.put("type",1);
//        Map<String, String> forms = WYYEncryptUtil.encrypt(req_data);
        String result = Requests.post(SEARCHURL).headers(headers).params(forms).send().readToText();
        System.out.println(result);
        return songs;
    }





    private String getLyric(String id){
        String data = "{\"id\":" + id + ",\"lv\":-1,\"tv\":-1,\"csrf_token\":\"\"}";
        Map<String, String> forms = WYYEncryptUtil.encrypt(data);
        String text = Requests.post(LYRICURL).headers(headers).forms(forms).send().readToText();
        JSONObject object = JSONObject.parseObject(text);
        try {
            String lyric = object.getJSONObject("lrc").getString("lyric");
            return lyric;
        }catch (NullPointerException e){
            return "";
        }
    }
    public String getSongUrl(String id){
        String data = "{\"ids\":" + Arrays.asList(id) + ",\"br\":320000,\"csrf_token\":\"\"}";
        Map<String, String> forms = WYYEncryptUtil.encrypt(data);
        String text = Requests.post(SONGURL).headers(headers).forms(forms).send().readToText();
        JSONArray array = JSONObject.parseObject(text).getJSONArray("data");
        if(!array.isEmpty()){
            return array.getJSONObject(0).getString("url");
        }
        return "";
    }
    private String getSinger(JSONArray array){
        StringBuilder singer = new StringBuilder();
        for(int i =0;i<array.size();i++){
            JSONObject object = array.getJSONObject(i);
            singer.append(object.getString("name"));
            singer.append("/");
        }
        return singer.deleteCharAt(singer.length()-1).toString();
    }
}
