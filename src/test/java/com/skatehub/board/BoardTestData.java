package com.skatehub.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;

/** Synthetic controls stay in memory and are never added to the real normalized file. */
final class BoardTestData {
    private BoardTestData() {}
    static JsonNode real(BoardDatasetValidator validator) {
        try(var stream=Files.newInputStream(Path.of("docs/equipment-data/normalized-equipment.v1.json"))){return validator.read(stream);}
        catch(IOException e){throw new IllegalStateException(e);}
    }
    static ObjectNode synthetic(BoardDatasetValidator validator) {
        JsonNode real=real(validator);ObjectNode d=validator.object();d.put("schemaVersion",1);d.put("datasetVersion","synthetic-v1");
        for(String group:BoardDatasetValidator.GROUPS)d.set(group,validator.array());d.set("mapping",validator.array());d.set("gaps",validator.array());
        ObjectNode p=(ObjectNode)find(real,"products","SET-002").deepCopy();
        p.put("key","SYNTHETIC-P");p.put("sourceRecordId","SYNTHETIC-P");p.put("brand","SYNTHETIC");p.put("model","Fictional complete, validator only");p.put("active",true);p.set("imageSource",validator.object());p.remove("notes");
        ObjectNode v=(ObjectNode)real.path("variants").findParents("productKey").stream().filter(x->"SET-002".equals(x.path("productKey").asText())).findFirst().orElseThrow().deepCopy();
        v.put("key","SYNTHETIC-V");v.put("productKey","SYNTHETIC-P");v.put("active",true);v.put("verificationStatus","VERIFIED");((ObjectNode)v.path("specs")).put("manufacturerComplete",true);v.put("label","SYNTHETIC complete");
        ObjectNode q=(ObjectNode)find(real,"quotes","SET-002--cn-price").deepCopy();
        q.put("key","SYNTHETIC-Q");q.put("sourceProductKey","SYNTHETIC-P");q.put("variantKey","SYNTHETIC-V");q.put("active",true);q.put("verificationStatus","VERIFIED");q.put("packageQuantity",1);q.put("packageUnit","COMPLETE");q.put("shippingCents",0);q.put("taxCents",0);q.put("landedPriceConfirmed",true);q.put("amountCents",10010);q.put("purchaseScope","SYNTHETIC one complete product");q.put("displayPrice","100.10 CNY SYNTHETIC");q.put("collectedAt",Instant.now().minusSeconds(120).toString());q.put("validUntil",Instant.now().plusSeconds(3600).toString());q.remove("rawPrice");
        for(ObjectNode row:new ObjectNode[]{p,v,q}) {
            ObjectNode source=validator.object();source.put("url","https://example.invalid/synthetic-only");source.put("title","SYNTHETIC not a manufacturer fact");source.put("collectedAt",Instant.now().minusSeconds(120).toString());source.put("verificationStatus","VERIFIED");source.put("evidenceRef","synthetic-memory-only");source.put("note","Fictional test control");row.set("sources",validator.array().add(source));
        }
        ((com.fasterxml.jackson.databind.node.ArrayNode)d.get("products")).add(p);((com.fasterxml.jackson.databind.node.ArrayNode)d.get("variants")).add(v);((com.fasterxml.jackson.databind.node.ArrayNode)d.get("quotes")).add(q);
        ObjectNode m=validator.object();m.put("sourceRecordId","SYNTHETIC-P");m.put("productKey","SYNTHETIC-P");m.put("sourcePath","synthetic-memory");m.put("sourceRecordSha256","0".repeat(64));m.put("basis","synthetic only");m.set("variantKeys",validator.array().add("SYNTHETIC-V"));m.set("quoteKeys",validator.array().add("SYNTHETIC-Q"));m.set("ruleKeys",validator.array());((com.fasterxml.jackson.databind.node.ArrayNode)d.get("mapping")).add(m);
        return d;
    }
    static ObjectNode find(JsonNode dataset,String group,String key){for(JsonNode node:dataset.path(group))if(key.equals(node.path("key").asText())||key.equals(node.path("sourceRecordId").asText()))return (ObjectNode)node;throw new IllegalArgumentException(key);}
}
