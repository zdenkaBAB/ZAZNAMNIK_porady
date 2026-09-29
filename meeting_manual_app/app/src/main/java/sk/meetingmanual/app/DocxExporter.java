package sk.meetingmanual.app;
import java.io.*;import java.util.zip.*;import java.nio.charset.StandardCharsets;
public class DocxExporter {
 static String esc(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
 static void put(ZipOutputStream z,String n,String s)throws Exception{z.putNextEntry(new ZipEntry(n));z.write(s.getBytes(StandardCharsets.UTF_8));z.closeEntry();}
 public static File export(File dir,String title,String transcript,String audioName)throws Exception{
   dir.mkdirs();File f=new File(dir,(title.isEmpty()?"stretnutie":title.replaceAll("[^\\p{L}\\p{N}._-]","_")+"_manual")+".docx");
   String body="<w:p><w:r><w:rPr><w:b/></w:rPr><w:t>MANUÁL / ZÁZNAM ZO STRETNUTIA</w:t></w:r></w:p>"+
    "<w:p><w:r><w:t>Názov: "+esc(title)+"</w:t></w:r></w:p>"+
    "<w:p><w:r><w:rPr><w:b/></w:rPr><w:t>HLAVNÉ TÉMY A POKYNY</w:t></w:r></w:p>"+
    paragraphs(transcript)+
    "<w:p><w:r><w:rPr><w:b/></w:rPr><w:t>ZDROJOVÝ ZÁZNAM</w:t></w:r></w:p>"+
    "<w:p><w:r><w:t>Audio súbor: "+esc(audioName==null?"":audioName)+"</w:t></w:r></w:p>";
   String document="<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>"+body+"<w:sectPr/></w:body></w:document>";
   try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(f))){
    put(z,"[Content_Types].xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>");
    put(z,"_rels/.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>");
    put(z,"word/_rels/document.xml.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"></Relationships>"); put(z,"word/document.xml",document);
   } return f;
 }
 static String paragraphs(String s){StringBuilder b=new StringBuilder();for(String p:s.split("\\n")){if(p.trim().isEmpty())continue;b.append("<w:p><w:r><w:t xml:space=\"preserve\">").append(esc(p)).append("</w:t></w:r></w:p>");}return b.toString();}
}
