package ispd.arquivo.exportador;

import ispd.arquivo.xml.utils.WrappedDocument;
import ispd.arquivo.xml.utils.WrappedElement;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Utility class to convert an iSPD file to GridSim java file.
 * Construct it and call method {@link #export()}.
 */
class GridSimExporter {
    private final HashMap<Integer, String> resources = new HashMap<>(0);

    private final NodeList machines;
    private final NodeList clusters;
    private final NodeList internet;
    private final NodeList loads;

    private final PrintWriter out;
    private final WrappedDocument doc;
    private final int userCount;

    /* package-private */ GridSimExporter(final Document model,
                                          final PrintWriter out) {
        this.doc = new WrappedDocument(model);
        this.out = out;

        this.userCount = (int) this.doc.owners().count();

        this.machines = model.getElementsByTagName("machine");
        this.clusters = model.getElementsByTagName("cluster");
        this.internet = model.getElementsByTagName("internet");
        this.loads = model.getElementsByTagName("load");

        this.printHeader();
        this.printMain();
    }

    private void printHeader() {
        this.out.print("""
                    
                import java.util.*;
                import gridsim.*;
                import gridsim.net.*;
                    
                class Mestre extends GridSim {
                    
                    GridletList list;
                    private Integer ID_;
                    public Router r;
                    ArrayList Escravos_;
                    int Escal;
                    
                    
                    Mestre(String nome, Link link,GridletList list, ArrayList Escravo, int esc) throws Exception {
                        super(nome, link);
                        this.list = list;
                        this.ID_ = new Integer(getEntityId(nome));
                        this.Escravos_ = Escravo;
                        this.Escal=esc;
                    }
                    
                    @Override
                    public void body() {
                    
                        ArrayList<GridResource> resList = this.Escravos_;
                        int ids[] = new int[resList.size()];
                        double temp_ini, temp_fim;
                        
                        while (true) {
                            super.gridSimHold(2.0);
                            LinkedList recur = GridSim.getGridResourceList();
                            if (recur.size() > 0)
                                break;
                        }
                        
                        for(int j=0;j<resList.size(); j++){
                            ids[j] = resList.get(j).get_id();
                        }
                    
                        for(int i = 0; i < resList.size(); i++){
                            super.send(ids[i], GridSimTags.SCHEDULE_NOW, GridSimTags.RESOURCE_CHARACTERISTICS, this.ID_);
                        }
                        temp_ini = GridSim.clock();
                        if(this.Escal==1){ //O escalonador é Workqueue
                            int cont=0; int k; Gridlet gl;
                            for(k=0; k < Escravos_.size() && cont < list.size(); k++, cont++){
                                int num = resList.get(k).get_id();;
                                list.get(cont).setUserID(this.ID_);
                                super.gridletSubmit((Gridlet)list.get(cont),num , 0.0, true);
                            }
                            int res=0;
                            while(cont<list.size() || res<list.size()) {
                                 gl = super.gridletReceive();
                                res++;
                                int num = gl.getResourceID();
                                if(cont<list.size()){
                                    list.get(cont).setUserID(this.ID_);
                                    super.gridletSubmit((Gridlet)list.get(cont),num , 0.0, true);
                                    cont++;
                                }
                            }
                        }else{//É RoundRobin
                        
                        }
                        temp_fim = GridSim.clock();
                        System.out.println("TEMPO DE SIMULAÇÂO:"+(temp_fim-temp_ini));
                        super.shutdownGridStatisticsEntity();
                        super.shutdownUserEntity();
                         super.terminateIOEntities();
                         }
                    }
                """);
    }

    private void printMain() {
        this.out.print(MessageFormat.format("""
                                        
                        class Modelo'{'

                          	public static void main(String[] args) '{'

                        		try '{'
                        			Calendar calendar = Calendar.getInstance();
                        			 boolean trace_flag = true;
                        			String[] exclude_from_file = '{'""'}';
                        			 String[] exclude_from_processing = '{'""'}';
                        			GridSim.init({0},calendar, true, exclude_from_file,exclude_from_processing, null);

                        			FIFOScheduler resSched = new FIFOScheduler( " GridResSched ");
                                    double baud_rate = 100.0;
                                    double delay =0.1;
                                    int MTU = 100;""",

                this.userCount));

        this.printResources();

        this.out.print(this.getLoadTraceString());

        this.printMasters();

        this.out.print("""

                            ResourceUserList userList = createGridUser();
                """);

        this.printInternet();
        this.printNonMasterConnection();

        this.out.print("""

                            GridSim.startGridSimulation();
                                } catch (Exception e){
                              e.printStackTrace();
                             System.out.println("Unwanted ERRORS happened");
                        }
                    }
                """);
    }

    private void printResources() {
        this.printMachines();
        this.printClusters();
    }

    private String getLoadTraceString() {
        if (this.loads.getLength() == 0)
            return "";

        final var trace =
                ((Element) this.loads.item(0)).getElementsByTagName("trace");

        if (trace.getLength() == 0)
            return """
                        
                                GridletList list = createGridlet();
                        
                    """;

        return MessageFormat.format("""

                             String[] fileName = '{'
                                {0}

                            '}'

                             ArrayList load = new ArrayList();
                             for (i = 0; i < fileName.length; i++)'{'
                                Workload w = new Workload("Load_"+i, fileName[i], resList[], rating);
                                load.add(w);
                            '}'
                """, ((Element) trace.item(0)).getAttribute("file_path"));
    }

    private void printMasters() {
        this.out.printf("""

                Link link = new SimpleLink("link_", 100, 0.01, 1500 );
                """);

        for (int i = 0; i < this.machines.getLength(); i++)
            this.printMaster((Element) this.machines.item(i), i);
    }

    private void printInternet() {
        GridSimExporter.asStream(this.internet)
                .forEach(net -> {
                    final var id = net.getAttribute("id");

                    this.resources.put(
                            GridSimExporter.globalId(net),
                            id
                    );

                    this.out.print("""
                            Router r_%s = new RIPRouter(%s, trace_flag);
                            """.formatted(id, id));
                });
    }

    private void printNonMasterConnection() {
        this.out.print("""
                    
                            FIFOScheduler rSched = new FIFOScheduler("r_Sched");
                """);

        final var ls = this.doc.links().toList();

        for (int i = 0; i < ls.size(); ++i)
        {
            this.printLink(ls.get(i), i);
        }
    }

    private void printLink(final WrappedElement e, final int id) {
        this.out.print(String.format("""
                            
                                    Link %s = new SimpleLink("link_%d", %s*1000, %s*1000,1500  );
                        """,
                e.id(),
                id,
                e.bandwidth(),
                e.latency()
        ));
    }

    private void printMachines() {
        for (int i = 0; i < this.machines.getLength(); i++) {
            final var machine = (Element) this.machines.item(i);
            if (machine.getElementsByTagName("master").getLength() == 0)
                this.printResource(machine, i, 1);
        }
    }

    private void printClusters() {
        for (int j = 0, i = this.machines.getLength(); i < this.machines.getLength() + this.clusters.getLength(); i++, j++) {
            final var cluster = (Element) this.clusters.item(j);
            final int nodes = Integer.parseInt(cluster.getAttribute("nodes"));
            this.printResource(cluster, i, nodes);
        }
    }

    private void printMaster(final Element machine, final int id) {
        final var e = new WrappedElement(machine);

        if (!e.hasMasterAttribute())
            return;

        this.resources.put(e.globalIconId(), e.id());

        final var slaves = e.master().slaves().toList();

        this.out.print(MessageFormat.format("""

                            ArrayList esc{0} = new ArrayList();
                """, id));

        slaves.stream()
                .map(WrappedElement::id)
                .map(Integer::parseInt)
                .forEach(i -> this.out.print("""
                                    esc%d.add(%s);
                        """.formatted(id, this.resources.get(i))));

        this.out.print(MessageFormat.format("""

                                    Mestre {0} = new Mestre("{0}_", link, list, esc{1}, {2});
                                    Router r_{0} = new RIPRouter( "router_{2}", trace_flag);
                                    r_{0}.attachHost( {0}, resSched);
                        """,
                e.id(),
                id,
                slaves.size()
        ));

        slaves.stream()
                .map(WrappedElement::id)
                .map(Integer::parseInt)
                .forEach(i -> this.out.print(("""

                                    r_%s.attachHost( %s, resSched);
                        """).formatted(e.id(), this.resources.get(i))));
    }

    private static Stream<Element> asStream(final NodeList list) {
        return IntStream.range(0, list.getLength()).mapToObj(list::item).map(Element.class::cast);
    }

    private static int globalId(final Element machine) {
        return new WrappedElement(machine).globalIconId();
    }

    private void printResource(final Element machine,
                               final int index,
                               final int nodes) {
        final var e = new WrappedElement(machine);

        this.resources.put(e.globalIconId(), e.id());

        this.out.print(MessageFormat.format("""

                                    GridResource {0} = createResource("{0}_",  baud_rate,  delay,  MTU, {1}, (int){2});
                                    Router r_{0} = new RIPRouter( "router_{3}", trace_flag);
                                    r_{0}.attachHost( {0}, resSched);
                        """,
                e.id(),
                nodes,
                e.power(),
                index
        ));
    }

    /**
     * Export model to FilePrinter {@link #out} passed in the constructor.
     */
    public void export() {


        this.printCreateGridUser();
        this.printCreateResource();
        this.printCreateGridlet();

        this.printFooter();
    }

    private void printCreateGridUser() {
        this.out.print(String.format("""

                    private static ResourceUserList createGridUser(){
                        ResourceUserList userList = new ResourceUserList();
                        %s
                        return userList;
                    }
                    
                """, this.userAdds())
        );
    }

    private void printCreateResource() {
        this.out.print("""
                    
                    private static GridResource createResource(String name, double baud_rate, double delay, int MTU, int n_maq, int cap){
                    
                            MachineList mList = new MachineList();
                            for(int i = 0; i < n_maq; i++){
                                
                             mList.add( new Machine(i, 1, cap));
                        }
                    
                            String arch = "Sun Ultra";
                            String os = "Solaris";
                            double time_zone = 9.0;
                            double cost = 3.0;
                    
                        ResourceCharacteristics resConfig = new ResourceCharacteristics(arch, os, mList, ResourceCharacteristics.TIME_SHARED,time_zone, cost);
                    
                        long seed = 11L*13*17*19*23+1;
                        double peakLoad = 0.0;
                        double offPeakLoad = 0.0;
                        double holidayLoad = 0.0;
                    
                        LinkedList Weekends = new LinkedList();
                        Weekends.add(new Integer(Calendar.SATURDAY));
                        Weekends.add(new Integer(Calendar.SUNDAY));
                        LinkedList Holidays = new LinkedList();
                        GridResource gridRes=null;
                    
                        try
                         {
                             gridRes = new GridResource(name, new SimpleLink(name + "_link", baud_rate, delay, MTU),seed, resConfig, peakLoad, offPeakLoad, holidayLoad,Weekends, Holidays);
                    
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                    
                        return gridRes;
                    }
                """);
    }

    private void printCreateGridlet() {
        this.out.print("""


                    private static GridletList createGridlet(){
                        double length;
                        long file_size;
                        Random random = new Random();

                        GridletList list = new GridletList();
                """);

        GridSimExporter.iter(this.loads)
                .forEach(e -> this.processLoadValues(
                        e.getElementsByTagName("size")));

        this.out.print("""

                        return list;
                """);
    }

    private void printFooter() {
        this.out.print("""

                    }
                }
                """);
    }

    private String userAdds() {
        return IntStream.range(0, this.userCount)
                .mapToObj(GridSimExporter::addUserId)
                .collect(Collectors.joining());
    }

    private static Iterable<Element> iter(final NodeList list) {
        return () -> new NodeListIterator(list);
    }

    private void processLoadValues(final NodeList sizes) {
        double minComputation = 0;
        double maxComputation = 0;
        double computationValue = 0;
        double communicationValue = 0;
        double mincp = 0;
        double maxcp = 0;
        double mincm = 0;
        double maxcm = 0;

        for (int i = 0; i < sizes.getLength(); i++) {
            final Element size = (Element) sizes.item(i);

            final var e = new WrappedElement(size);

            if (e.isComputingType()) {
                minComputation = e.minimum();
                maxComputation = e.maximum();
                computationValue = e.average();
                mincp = (computationValue - minComputation) / computationValue;
                mincp = Math.min(1.0, mincp);
                maxcp = (maxComputation - computationValue) / computationValue;
                maxcp = Math.min(1.0, maxcp);
            } else if (e.isCommunicationType()) {
                communicationValue = e.average();
                mincm = (communicationValue - minComputation) / communicationValue;
                mincp = Math.min(1.0, mincm);
                maxcm = (maxComputation - communicationValue) / communicationValue;
                maxcp = Math.min(1.0, maxcm);
            }

            this.out.print(MessageFormat.format("""
                                    length = GridSimRandom.real({0},{1},{2},random.nextDouble());
                                    file_size = (long) GridSimRandom.real({3},{4},{5},random.nextDouble());
                                    Gridlet gridlet{6} = new Gridlet({6}, length, file_size,file_size);
                                    list.add(gridlet{6});

                                    gridlet{6}.setUserID(0);
                            """,
                    computationValue, mincp, maxcp,
                    communicationValue, mincm, maxcm,
                    i
            ));
        }
    }

    private static String addUserId(final int i) {
        return String.format("""
                        userList.add(%d);
                """, i);
    }

    private static class NodeListIterator implements Iterator<Element> {

        private final NodeList list;
        private int index;

        private NodeListIterator(final NodeList list) {
            this.list = list;
            this.index = 0;
        }

        @Override
        public boolean hasNext() {
            return this.index < this.list.getLength();
        }

        @Override
        public Element next() {
            if (!this.hasNext())
                throw new NoSuchElementException("No more elements in list");

            final var elem = (Element) this.list.item(this.index);

            this.index++;

            return elem;
        }
    }
}