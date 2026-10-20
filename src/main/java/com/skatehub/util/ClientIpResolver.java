package com.skatehub.util;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

/** Literal IPs only (no DNS/ports/zones). Untrusted or malformed forwarding falls back to the socket peer. */
public final class ClientIpResolver {
    private ClientIpResolver() {}

    public static String resolve(String remoteAddr, String forwarded, List<String> trustedProxies) {
        String peer=literal(remoteAddr);
        if(peer==null)return "unknown";
        if(!trusted(peer,trustedProxies)||forwarded==null||forwarded.isBlank()||forwarded.length()>4096)return peer;
        String[] values=forwarded.split(",",-1);
        if(values.length>32)return peer;
        List<String> chain=new ArrayList<>();
        for(String value:values){String ip=literal(value.trim());if(ip==null)return peer;chain.add(ip);}
        String current=peer;
        for(int i=chain.size()-1;i>=0&&trusted(current,trustedProxies);i--)current=chain.get(i);
        return current;
    }

    public static void validateTrustedProxy(String network) {
        String[] parts=network.split("/",-1);
        if(parts.length>2||literal(parts[0])==null)throw new IllegalArgumentException("Trusted proxy must be a literal IP or CIDR");
        if(parts.length==2&&!parts[1].matches("[0-9]{1,3}"))throw new IllegalArgumentException("Invalid trusted proxy CIDR prefix");
        new IpAddressMatcher(network); // Existing Spring Security implementation validates IPv4/IPv6 prefix length.
    }

    private static boolean trusted(String ip,List<String> networks) {
        for(String network:networks)if(new IpAddressMatcher(network).matches(ip))return true;
        return false;
    }

    private static String literal(String value) {
        if(value==null||value.isEmpty()||!value.equals(value.trim()))return null;
        if(!value.contains(":")){
            String[] parts=value.split("\\.",-1);
            if(parts.length!=4)return null;
            for(String part:parts)if(!part.matches("0|[1-9][0-9]{0,2}")||Integer.parseInt(part)>255)return null;
        }else if(!value.matches("[0-9a-fA-F:.]+"))return null;
        try{return InetAddress.getByName(value).getHostAddress();}catch(java.net.UnknownHostException error){return null;}
    }
}
