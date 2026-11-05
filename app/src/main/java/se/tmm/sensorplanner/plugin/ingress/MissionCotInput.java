package se.tmm.sensorplanner.plugin.ingress;

import android.annotation.TargetApi;
import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.*;
import org.xml.sax.ext.DefaultHandler2;

/** Bounded fixed-canary event extraction only; no source authority, import or storage. */
@TargetApi(26)
public final class MissionCotInput {
    private static final Pattern DECLARATION = Pattern.compile("<\\?xml[ \\t\\r\\n]+version[ \\t\\r\\n]*=[ \\t\\r\\n]*(['\"])1\\.0\\1"
            + "(?:[ \\t\\r\\n]+encoding[ \\t\\r\\n]*=[ \\t\\r\\n]*(['\"])(?i:UTF-8)\\2)?"
            + "(?:[ \\t\\r\\n]+standalone[ \\t\\r\\n]*=[ \\t\\r\\n]*(['\"])(?:yes|no)\\3)?[ \\t\\r\\n]*\\?>");
    private MissionCotInput() { }
    public static final class Batch {
        public final int events;
        public final List<String> canaries;
        private Batch(int events,List<String> values){this.events=events;canaries=Collections.unmodifiableList(new ArrayList<>(values));}
    }
    public static Batch read(InputStream input,BooleanSupplier active)throws IOException {
        try {
            String xml=xmlText(input,active);
            Handler handler=new Handler(active);
            XMLReader reader=reader(handler);
            reader.parse(new InputSource(new StringReader(xml)));
            if(!handler.complete||!active.getAsBoolean())throw new IOException();
            return new Batch(handler.events,handler.canaries);
        }catch(Exception invalid){throw new IOException("Lab metadata response could not be verified");}
        catch(javax.xml.parsers.FactoryConfigurationError invalid){throw new IOException("Lab XML reader unavailable");}
    }
    static String xmlText(InputStream input,BooleanSupplier active)throws IOException {
        String xml=MissionReadInput.readText(input,active);
        if(xml.contains("<!DOCTYPE")||xml.startsWith("\ufeff"))throw new IOException();
        if(xml.startsWith("<?xml")){
            int end=xml.indexOf("?>");
            if(end<0||!DECLARATION.matcher(xml.substring(0,end+2)).matches())throw new IOException();
            xml=xml.substring(end+2);
        }
        return xml;
    }
    static XMLReader reader(DefaultHandler2 handler)throws Exception {
        SAXParserFactory factory=SAXParserFactory.newInstance();factory.setNamespaceAware(true);factory.setValidating(false);
        XMLReader reader=factory.newSAXParser().getXMLReader();
        reader.setProperty("http://xml.org/sax/properties/lexical-handler",handler);
        reader.setContentHandler(handler);reader.setErrorHandler(handler);reader.setEntityResolver(handler);return reader;
    }
    private static final class Handler extends DefaultHandler2 {
        final BooleanSupplier active;
        final Set<String> uids=new HashSet<>();
        final List<String> canaries=new ArrayList<>();
        int depth,events;boolean complete;StringBuilder event;
        Handler(BooleanSupplier active){this.active=active;}
        private void check()throws SAXException {if(Thread.currentThread().isInterrupted()||!active.getAsBoolean())throw invalid();}
        @Override public void startElement(String uri,String local,String name,Attributes attrs)throws SAXException {
            check();if(++depth>16||!uri.isEmpty()||!local.equals(name)||attrs.getLength()>64)throw invalid();
            if(depth==1){if(!"events".equals(name)||attrs.getLength()!=0)throw invalid();}
            else if(depth==2){
                if(!"event".equals(name)||++events>64)throw invalid();
                String uid=attrs.getValue("uid");if(uid==null||uid.isEmpty()||!uids.add(uid))throw invalid();
                if(IngressProbeState.isCanary(uid))event=new StringBuilder();
            }else if("event".equals(name)||"events".equals(name))throw invalid();
            if(event!=null)event.append('<').append(name);
            for(int i=0;i<attrs.getLength();i++){
                String key=attrs.getQName(i),value=attrs.getValue(i);
                if(!attrs.getURI(i).isEmpty()||key.length()>128||value.length()>4096)throw invalid();
                if(event!=null)event.append(' ').append(key).append("=\"").append(escape(value)).append('"');
            }
            if(event!=null){event.append('>');bound();}
        }
        @Override public void endElement(String uri,String local,String name)throws SAXException {
            check();if(event!=null){event.append("</").append(name).append('>');bound();}
            if(depth==2&&event!=null){canaries.add(event.toString());event=null;}
            if(--depth==0)complete=true;
        }
        @Override public void characters(char[] text,int start,int length)throws SAXException {
            check();if(event!=null){event.append(escape(new String(text,start,length)));bound();}
            else if(depth<=1)for(int i=start;i<start+length;i++)if(!Character.isWhitespace(text[i]))throw invalid();
        }
        private void bound()throws SAXException {if(event.length()>MissionReadInput.MAX_BYTES)throw invalid();}
        private static String escape(String value){return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("\r","&#13;").replace("\n","&#10;").replace("\t","&#9;");}
        @Override public void startPrefixMapping(String prefix,String uri)throws SAXException {throw invalid();}
        @Override public void processingInstruction(String target,String data)throws SAXException {throw invalid();}
        @Override public void startDTD(String name,String publicId,String systemId)throws SAXException {throw invalid();}
        @Override public void startCDATA()throws SAXException {throw invalid();}
        @Override public void comment(char[] text,int start,int length)throws SAXException {throw invalid();}
        @Override public InputSource resolveEntity(String publicId,String systemId)throws SAXException {throw invalid();}
        @Override public InputSource resolveEntity(String name,String publicId,String base,String systemId)throws SAXException {throw invalid();}
        @Override public void error(SAXParseException error)throws SAXException {throw invalid();}
        @Override public void fatalError(SAXParseException error)throws SAXException {throw invalid();}
        private SAXException invalid(){return new SAXException("Invalid bounded lab events");}
    }
}
