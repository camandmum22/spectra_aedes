package controllers;

import models.EntidadSaludLocal;
import models.EntidadSaludRegional;
import models.Usuario;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.export.*;
import play.mvc.Controller;
import play.mvc.With;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static play.db.jpa.JPA.em;

/**

 */
@With(Secure.class)
public class Reportes extends Controller {

    private static HashMap<String, String> reportViews = new HashMap<String, String>(5);

    static {
        reportViews.put("Indice_de_Breteau", "indicebreteau");
        reportViews.put("Indice_de_Casas_Positivas", "indicecasas");
        reportViews.put("Indice_de_Pupas", "indicepupas");
        reportViews.put("Indice_de_Recipientes", "\"Indicerecipientes\"");
        reportViews.put("Reporte_de_Criaderos", "contenedorespositivos ");
        reportViews.put("Indice_de_Contenedores_Positivos", "indicecasaspositivas");//new add
    }

    public static enum RenderType {
        XLS_RENDER_TYPE("xls", "application/vnd.ms-excel", "attachment"),
        PDF_RENDER_TYPE("pdf", "application/pdf", "inline"),
        XML_RENDER_TYPE("xml", "text/xml", "inline"),
        HTML_RENDER_TYPE("html", "text/html", null);

        RenderType(String type, String contentType, String contentDisposition) {
            this.type = type;
            this.contentType = contentType;
            this.contentDisposition = contentDisposition;
        }

        private String type;
        private String contentType;
        private String contentDisposition;

        public static RenderType getByType(String type) {
            switch (type) {
                case "xls":
                    return XLS_RENDER_TYPE;
                case "pdf":
                    return PDF_RENDER_TYPE;
                case "xml":
                    return XML_RENDER_TYPE;
                default:
                    return HTML_RENDER_TYPE;
            }
        }
    }

    public final static File REPORT_DEFINITION_PATH = new File("C:/Users/Camilo Andres/Dropbox/Investigacion i2t/Aedes 2/AedesServer/reports/"); //new File("./reports/"); //chaged
    public final static File REPORTS_RENDER_DIR = new File(REPORT_DEFINITION_PATH, "public/");
    public final static File REPORTS_IMAGES_DIR = new File(REPORTS_RENDER_DIR, "images/"); 

    public static void index() {
        Usuario usuario = Security.connectedUser();
        boolean chooseRegionAccess = Security.check("secretariaNacional");
        boolean chooseLocalAccess = chooseRegionAccess || Security.check("secretariaRegional");
        render(usuario, chooseRegionAccess, chooseLocalAccess);
    }

    public static void renderReport() {
        String reportDefFile = params.get("name");
        RenderType renderType = RenderType.getByType(params.get("type"));
        HashMap<String, Object> renderArgs = new HashMap<>();
        renderArgs.put("Ano", Integer.parseInt(params.get("year")));
        renderArgs.put("Mes", params.get("month"));

        try {
            String compiledFile = REPORT_DEFINITION_PATH.getAbsolutePath() + "/" + reportDefFile + ".jasper";

            if (!new File(compiledFile).exists()) {

                JasperCompileManager.compileReportToFile(REPORT_DEFINITION_PATH + "/" +
                        reportDefFile + ".jrxml", compiledFile);
            }


            JasperPrint jasperPrint = JasperFillManager.fillReport(compiledFile,
                    renderArgs, play.db.DB.getConnection());

            OutputStream os = new ByteArrayOutputStream();
            JRExporter exporter;

            if (renderType.equals(RenderType.HTML_RENDER_TYPE)) {
                exporter = new JRHtmlExporter();
                exporter.setParameter(JRExporterParameter.OUTPUT_FILE, new File(REPORTS_RENDER_DIR, reportDefFile + ".html"));
                exporter.setParameter(JRHtmlExporterParameter.IMAGES_DIR, REPORTS_IMAGES_DIR);
                exporter.setParameter(JRHtmlExporterParameter.IS_USING_IMAGES_TO_ALIGN, false);
            } else {
                if (renderType.equals(RenderType.XLS_RENDER_TYPE)) {
                    exporter = new JExcelApiExporter();
                    exporter.setParameter(JExcelApiExporterParameter.IS_DETECT_CELL_TYPE, true);
                } else if (renderType.equals(RenderType.XML_RENDER_TYPE)) {
                    exporter = new JRXml4SwfExporter();
                } else { //PDF_RENDER_TYPE
                    exporter = new JRPdfExporter();
                    exporter.setParameter(JRPdfExporterParameter.METADATA_AUTHOR, "Cideim");
                }
                exporter.setParameter(JRExporterParameter.OUTPUT_STREAM, os);
            }


            exporter.setParameter(JRExporterParameter.JASPER_PRINT, jasperPrint);
            exporter.exportReport();

            response.setContentTypeIfNotSet(renderType.contentType);

            if (renderType.equals(RenderType.HTML_RENDER_TYPE)) {
                redirect("/reports/public/" + reportDefFile + ".html");
            } else {
                response.setHeader("Content-Disposition", renderType.contentDisposition +
                        "; filename=\"" + reportDefFile + "." + renderType.type + "\"");
                renderBinary(new ByteArrayInputStream(((ByteArrayOutputStream) os).toByteArray()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class FechaReporte {
        String year;
        List<String> months = new ArrayList<String>();

        FechaReporte(String year) {
            this.year = year;
        }

        void setMonths(List months) {
            for (Object m : months) {
                String month = String.valueOf(m);
                if (month.length() == 1) month = "0" + month;
                this.months.add(month);
            }
        }
    }

    public static void fechasReporte(String entidadSaludLocal) {
        ArrayList<FechaReporte> fechas = new ArrayList<FechaReporte>();
        List years = em().createNativeQuery("SELECT DISTINCT \"Ano\" FROM indicebreteau ORDER BY \"Ano\"").getResultList();
        for (Object year : years) {
            FechaReporte fecha = new FechaReporte(String.valueOf(year).substring(0, 4));
            fecha.setMonths(em().createNativeQuery("SELECT DISTINCT \"Mes_num\" FROM indicebreteau" +
                    " WHERE \"Ano\" = " + fecha.year + " ORDER BY \"Mes_num\"").getResultList());
            fechas.add(fecha);
        }
        renderJSON(fechas);
    }

    public static void listaEntidadesSalud(String entidadSaludRegional) {
        if (entidadSaludRegional == null) {
            renderJSON(EntidadSaludRegional.findAll());
        } else {
            renderJSON(EntidadSaludLocal.find("byEntidadSaludRegional",
                    EntidadSaludRegional.findById(Long.parseLong(entidadSaludRegional))).fetch());
        }
    }
}
