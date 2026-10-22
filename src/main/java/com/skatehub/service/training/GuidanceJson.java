package com.skatehub.service.training;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;

/** JSON documents are the frozen v1 content format; validation remains server-side. */
public final class GuidanceJson {
    public static final ObjectMapper JSON = new ObjectMapper();
    private GuidanceJson() {}
    public static ObjectNode obj(Object... pairs) {
        ObjectNode n = JSON.createObjectNode();
        for (int i=0;i<pairs.length;i+=2) n.set((String)pairs[i], JSON.valueToTree(pairs[i+1]));
        return n;
    }
    public static ArrayNode arr() { return JSON.createArrayNode(); }
    public static ObjectNode parse(String s) {
        try { return (ObjectNode)JSON.readTree(s); } catch(Exception e) { throw new IllegalStateException("Invalid persisted guidance document",e); }
    }
    public static void check(boolean ok, String message) { if(!ok) throw new GuidanceException(400,"VALIDATION_ERROR",message); }
    public static GuidanceException missing() { return new GuidanceException(404,"NOT_FOUND","记录不存在"); }
    public static String text(JsonNode n,String key,int min,int max) {
        JsonNode v=n.path(key); check(v.isTextual() && v.asText().length()>=min && v.asText().length()<=max,key+" 长度或类型无效"); return v.asText();
    }
    public static int integer(JsonNode n,String key,int min,int max) {
        JsonNode v=n.path(key); check(v.isIntegralNumber() && v.canConvertToInt() && v.asInt()>=min && v.asInt()<=max,key+" 必须为范围内整数"); return v.asInt();
    }
    public static long number(JsonNode n,String key) {
        JsonNode v=n.path(key); check(v.isIntegralNumber() && v.canConvertToLong() && v.asLong()>=0,key+" 必须为非负整数"); return v.asLong();
    }
    public static String choice(JsonNode n,String key,String... choices) {
        String s=text(n,key,1,64); check(Arrays.asList(choices).contains(s), key+" 枚举无效"); return s;
    }
    public static LocalDate date(JsonNode n,String key) {
        try { String s=text(n,key,10,10); LocalDate d=LocalDate.parse(s); check(d.toString().equals(s),"日期格式无效"); return d; }
        catch(java.time.DateTimeException e) { throw new GuidanceException(400,"VALIDATION_ERROR",key+" 日期无效"); }
    }
    public static ArrayNode list(JsonNode n,String key,int min,int max) {
        JsonNode v=n.path(key); check(v.isArray() && v.size()>=min && v.size()<=max,key+" 数组无效"); return (ArrayNode)v;
    }
    public static String hash(JsonNode n) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical(n).toString().getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    private static JsonNode canonical(JsonNode n) {
        if(n.isObject()) { ObjectNode o=JSON.createObjectNode(); TreeSet<String> keys=new TreeSet<>(); n.fieldNames().forEachRemaining(keys::add); keys.forEach(k->o.set(k,canonical(n.get(k)))); return o; }
        if(n.isArray()) { ArrayNode a=arr(); n.forEach(v->a.add(canonical(v))); return a; }
        return n;
    }
    public static String uuid() { return UUID.randomUUID().toString(); }
    public static ObjectNode page(List<? extends JsonNode> data,int page,int size) {
        check(page>=1 && size>=1 && size<=100,"分页范围无效");
        int from=(int)Math.min((long)(page-1)*size,data.size());
        return obj("list",data.subList(from,Math.min(from+size,data.size())),"total",data.size(),"page",page,"size",size);
    }
}
