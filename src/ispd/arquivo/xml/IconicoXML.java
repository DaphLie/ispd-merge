package ispd.arquivo.xml;

import ispd.gui.PickModelTypeDialog;
import ispd.gui.iconico.Edge;
import ispd.gui.iconico.Vertex;
import ispd.gui.iconico.grade.Cluster;
import ispd.gui.iconico.grade.GridItem;
import ispd.gui.iconico.grade.Internet;
import ispd.gui.iconico.grade.Link;
import ispd.gui.iconico.grade.Machine;
import ispd.gui.iconico.grade.VirtualMachine;
import ispd.motor.carga.CargaForNode;
import ispd.motor.carga.CargaList;
import ispd.motor.carga.CargaRandom;
import ispd.motor.carga.CargaTrace;
import ispd.motor.carga.GerarCarga;
import ispd.motor.filas.RedeDeFilas;
import ispd.motor.filas.RedeDeFilasCloud;
import ispd.motor.filas.servidores.CS_Comunicacao;
import ispd.motor.filas.servidores.CS_Processamento;
import ispd.motor.filas.servidores.CentroServico;
import ispd.motor.filas.servidores.implementacao.CS_Internet;
import ispd.motor.filas.servidores.implementacao.CS_Link;
import ispd.motor.filas.servidores.implementacao.CS_Maquina;
import ispd.motor.filas.servidores.implementacao.CS_MaquinaCloud;
import ispd.motor.filas.servidores.implementacao.CS_Mestre;
import ispd.motor.filas.servidores.implementacao.CS_Switch;
import ispd.motor.filas.servidores.implementacao.CS_VMM;
import ispd.motor.filas.servidores.implementacao.CS_VirtualMac;
import ispd.motor.filas.servidores.implementacao.Vertice;
import ispd.motor.metricas.MetricasUsuarios;
import ispd.utils.ValidaValores;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Realiza manupulações com o arquivo xml do modelo icônico
 *
 * @author denison
 */
public class IconicoXML {
    private static final Element[] NO_CHILDREN = {};
    private static final Object[][] NO_ATTRS = {};
    private static final int DEFAULT_MODEL_TYPE = -1;
    private final Document doc =
            Objects.requireNonNull(ManipuladorXML.novoDocumento());
    private final Element system = this.doc.createElement("system");
    private Element load = null;

    public IconicoXML() {
        this(IconicoXML.DEFAULT_MODEL_TYPE);
    }

    public IconicoXML(final int modelType) {
        this.system.setAttribute("version",
                IconicoXML.getVersionForModelType(modelType));
        this.doc.appendChild(this.system);
    }

    /**
     * @throws IllegalArgumentException if modelType is not in -1, 0, 1 or 2
     */
    private static String getVersionForModelType(final int modelType) {
        return switch (modelType) {
            case PickModelTypeDialog.GRID -> "2.1";
            case PickModelTypeDialog.IAAS -> "2.2";
            case PickModelTypeDialog.PAAS -> "2.3";
            case IconicoXML.DEFAULT_MODEL_TYPE -> "1.2";
            default -> throw new IllegalArgumentException(
                    "Invalid model type " + modelType);
        };
    }

    /**
     * Este método sobrescreve ou cria arquivo xml do modelo iconico
     *
     * @param documento modelo iconico
     * @param arquivo   local que será salvo
     * @return indica se arquivo foi salvo corretamente
     */
    public static boolean escrever(final Document documento,
                                   final File arquivo) {
        return ManipuladorXML.escrever(documento, arquivo, "iSPD.dtd", false);
    }

    /**
     * Realiza a leitura de um arquivo xml contendo o modelo iconico
     * especificado pelo iSPD.dtd
     *
     * @param xmlFile endereço do arquivo xml
     * @return modelo iconico obtido do arquivo
     */
    public static Document ler(final File xmlFile) throws ParserConfigurationException, IOException, SAXException {
        return ManipuladorXML.ler(xmlFile, "iSPD.dtd");
    }

    /**
     * Verifica se modelo está completo
     *
     * @throws IllegalArgumentException
     */
    public static void validarModelo(final Document doc) {
        final var owner = doc.getElementsByTagName("owner");
        final var machine = doc.getElementsByTagName("machine");
        final var clusters = doc.getElementsByTagName("cluster");
        final var load = doc.getElementsByTagName("load");

        if (owner.getLength() == 0) {
            throw new IllegalArgumentException("The model has no users.");
        }

        if (machine.getLength() == 0 && clusters.getLength() == 0) {
            throw new IllegalArgumentException("The model has no icons.");
        }

        if (load.getLength() == 0) {
            throw new IllegalArgumentException("One or more  workloads have " +
                                               "not been configured.");
        }

        final boolean hasNoValidMaster = IntStream.range(0, machine.getLength())
                .mapToObj(machine::item)
                .map(Element.class::cast)
                .noneMatch(IconicoXML::isValidMaster);

        if (hasNoValidMaster) {
            throw new IllegalArgumentException("One or more parameters have " +
                                               "not been configured.");
        }
    }

    private static boolean isValidMaster(final Element m) {
        return m.getElementsByTagName("master").getLength() > 0;
    }

    /**
     * Converte um modelo iconico em uma rede de filas para o motor de simulação
     *
     * @param modelo Objeto obtido a partir do xml com a grade computacional
     *               modelada
     * @return Rede de filas simulável contruida conforme modelo
     */
    public static RedeDeFilas newRedeDeFilas(final Document modelo) {
        final NodeList docmaquinas = modelo.getElementsByTagName("machine");
        final NodeList docclusters = modelo.getElementsByTagName("cluster");
        final NodeList docinternet = modelo.getElementsByTagName("internet");
        final NodeList doclinks = modelo.getElementsByTagName("link");
        final NodeList owners = modelo.getElementsByTagName("owner");

        final HashMap<Integer, CentroServico> centroDeServicos =
                new HashMap<Integer, CentroServico>();
        final HashMap<CentroServico, List<CS_Maquina>> escravosCluster =
                new HashMap<CentroServico, List<CS_Maquina>>();
        final List<CS_Processamento> mestres =
                new ArrayList<CS_Processamento>();
        final List<CS_Maquina> maqs = new ArrayList<CS_Maquina>();
        final List<CS_VirtualMac> vms = new ArrayList<CS_VirtualMac>();
        final List<CS_Comunicacao> links = new ArrayList<CS_Comunicacao>();
        final List<CS_Internet> nets = new ArrayList<CS_Internet>();
        //cria lista de usuarios e o poder computacional cedido por cada um
        final HashMap<String, Double> usuarios = new HashMap<String, Double>();
        final HashMap<String, Double> perfis = new HashMap<String, Double>();
        for (int i = 0; i < owners.getLength(); i++) {
            final Element owner = (Element) owners.item(i);
            usuarios.put(owner.getAttribute("id"), 0.0);
            perfis.put(owner.getAttribute("id"),
                    Double.parseDouble(owner.getAttribute("powerlimit")));
        }
        //cria maquinas, mestres, internets e mestres dos clusters
        //Realiza leitura dos icones de máquina
        for (int i = 0; i < docmaquinas.getLength(); i++) {
            final Element maquina = (Element) docmaquinas.item(i);
            final Element id =
                    (Element) maquina.getElementsByTagName("icon_id").item(0);
            final int global = Integer.parseInt(id.getAttribute("global"));
            if (IconicoXML.isValidMaster(maquina)) {
                final Element master = (Element) maquina.getElementsByTagName(
                        "master").item(0);
                final CS_Mestre mestre = new CS_Mestre(
                        maquina.getAttribute("id"),
                        maquina.getAttribute("owner"),
                        Double.parseDouble(maquina.getAttribute("power")),
                        Double.parseDouble(maquina.getAttribute("load")),
                        master.getAttribute("scheduler")/*Escalonador*/,
                        Double.parseDouble(maquina.getAttribute("energy")));
                centroDeServicos.put(global, mestre);
                mestres.add(mestre);
                usuarios.put(mestre.getProprietario(),
                        usuarios.get(mestre.getProprietario()) + mestre.getPoderComputacional());
            } else {
                final CS_Maquina maq = new CS_Maquina(
                        maquina.getAttribute("id"),
                        maquina.getAttribute("owner"),
                        Double.parseDouble(maquina.getAttribute("power")),
                        1/*num processadores*/,
                        Double.parseDouble(maquina.getAttribute("load")),
                        Double.parseDouble(maquina.getAttribute("energy")));
                maqs.add(maq);
                centroDeServicos.put(global, maq);
                usuarios.put(maq.getProprietario(),
                        usuarios.get(maq.getProprietario()) + maq.getPoderComputacional());
            }
        }
        //Realiza leitura dos icones de cluster
        for (int i = 0; i < docclusters.getLength(); i++) {
            final Element cluster = (Element) docclusters.item(i);
            final Element id =
                    (Element) cluster.getElementsByTagName("icon_id").item(0);
            final int global = Integer.parseInt(id.getAttribute("global"));
            if (Boolean.parseBoolean(cluster.getAttribute("master"))) {
                final CS_Mestre clust = new CS_Mestre(
                        cluster.getAttribute("id"),
                        cluster.getAttribute("owner"),
                        Double.parseDouble(cluster.getAttribute("power")),
                        0.0,
                        cluster.getAttribute("scheduler")/*Escalonador*/,
                        Double.parseDouble(cluster.getAttribute("energy")));
                mestres.add(clust);
                centroDeServicos.put(global, clust);
                //Contabiliza para o usuario poder computacional do mestre
                final int numeroEscravos =
                        Integer.parseInt(cluster.getAttribute(
                                "nodes"));
                final double total =
                        clust.getPoderComputacional() + (clust.getPoderComputacional() * numeroEscravos);
                usuarios.put(clust.getProprietario(),
                        total + usuarios.get(clust.getProprietario()));
                final CS_Switch Switch = new CS_Switch(
                        cluster.getAttribute("id"),
                        Double.parseDouble(cluster.getAttribute("bandwidth")),
                        0.0,
                        Double.parseDouble(cluster.getAttribute("latency")));
                links.add(Switch);
                clust.addConexoesEntrada(Switch);
                clust.addConexoesSaida(Switch);
                Switch.addConexoesEntrada(clust);
                Switch.addConexoesSaida(clust);
                for (int j = 0; j < numeroEscravos; j++) {
                    final CS_Maquina maq = new CS_Maquina(
                            cluster.getAttribute("id"),
                            cluster.getAttribute("owner"),
                            Double.parseDouble(cluster.getAttribute("power")),
                            1/*numero de processadores*/,
                            0.0/*TaxaOcupacao*/,
                            j + 1/*identificador da maquina no cluster*/,
                            Double.parseDouble(cluster.getAttribute("energy")));
                    maq.addConexoesSaida(Switch);
                    maq.addConexoesEntrada(Switch);
                    Switch.addConexoesEntrada(maq);
                    Switch.addConexoesSaida(maq);
                    maq.addMestre(clust);
                    clust.addEscravo(maq);
                    maqs.add(maq);
                    //não adicionei referencia ao switch nem aos escrevos do
                    // cluster aos centros de serviços
                }
            } else {
                final CS_Switch Switch = new CS_Switch(
                        cluster.getAttribute("id"),
                        Double.parseDouble(cluster.getAttribute("bandwidth")),
                        0.0,
                        Double.parseDouble(cluster.getAttribute("latency")));
                links.add(Switch);
                centroDeServicos.put(global, Switch);
                //Contabiliza para o usuario poder computacional do mestre
                final double total = Double.parseDouble(cluster.getAttribute(
                        "power"))
                                     * Integer.parseInt(cluster.getAttribute(
                        "nodes"
                ));
                usuarios.put(cluster.getAttribute("owner"),
                        total + usuarios.get(cluster.getAttribute("owner")));
                final ArrayList<CS_Maquina> maqTemp =
                        new ArrayList<CS_Maquina>();
                final int numeroEscravos =
                        Integer.parseInt(cluster.getAttribute(
                                "nodes"));
                for (int j = 0; j < numeroEscravos; j++) {
                    final CS_Maquina maq = new CS_Maquina(
                            cluster.getAttribute("id"),
                            cluster.getAttribute("owner"),
                            Double.parseDouble(cluster.getAttribute("power")),
                            1/*numero de processadores*/,
                            0.0/*TaxaOcupacao*/,
                            j + 1/*identificador da maquina no cluster*/,
                            Double.parseDouble(cluster.getAttribute("energy")));
                    maq.addConexoesSaida(Switch);
                    maq.addConexoesEntrada(Switch);
                    Switch.addConexoesEntrada(maq);
                    Switch.addConexoesSaida(maq);
                    maqTemp.add(maq);
                    maqs.add(maq);
                }
                escravosCluster.put(Switch, maqTemp);
            }
        }

        //Realiza leitura dos icones de internet
        for (int i = 0; i < docinternet.getLength(); i++) {
            final Element inet = (Element) docinternet.item(i);
            final Element id =
                    (Element) inet.getElementsByTagName("icon_id").item(0);
            final int global = Integer.parseInt(id.getAttribute("global"));
            final CS_Internet net = new CS_Internet(
                    inet.getAttribute("id"),
                    Double.parseDouble(inet.getAttribute("bandwidth")),
                    Double.parseDouble(inet.getAttribute("load")),
                    Double.parseDouble(inet.getAttribute("latency")));
            nets.add(net);
            centroDeServicos.put(global, net);
        }
        //cria os links e realiza a conexão entre os recursos
        for (int i = 0; i < doclinks.getLength(); i++) {
            final Element link = (Element) doclinks.item(i);

            final CS_Link cslink = new CS_Link(
                    link.getAttribute("id"),
                    Double.parseDouble(link.getAttribute("bandwidth")),
                    Double.parseDouble(link.getAttribute("load")),
                    Double.parseDouble(link.getAttribute("latency")));
            links.add(cslink);

            //adiciona entrada e saida desta conexão
            final Element connect =
                    (Element) link.getElementsByTagName("connect").item(0);
            final Vertice origem =
                    (Vertice) centroDeServicos.get(Integer.parseInt(connect.getAttribute("origination")));
            final Vertice destino =
                    (Vertice) centroDeServicos.get(Integer.parseInt(connect.getAttribute("destination")));
            cslink.setConexoesSaida((CentroServico) destino);
            destino.addConexoesEntrada(cslink);
            cslink.setConexoesEntrada((CentroServico) origem);
            origem.addConexoesSaida(cslink);
        }
        //adiciona os escravos aos mestres
        for (int i = 0; i < docmaquinas.getLength(); i++) {
            final Element maquina = (Element) docmaquinas.item(i);
            final Element id =
                    (Element) maquina.getElementsByTagName("icon_id").item(0);
            final int global = Integer.parseInt(id.getAttribute("global"));
            if (IconicoXML.isValidMaster(maquina)) {
                final Element master = (Element) maquina.getElementsByTagName(
                        "master").item(0);
                final NodeList slaves = master.getElementsByTagName("slave");
                final CS_Mestre mestre =
                        (CS_Mestre) centroDeServicos.get(global);
                for (int j = 0; j < slaves.getLength(); j++) {
                    final Element slave = (Element) slaves.item(j);
                    final CentroServico maq =
                            centroDeServicos.get(Integer.parseInt(slave.getAttribute("id")));
                    if (maq instanceof CS_Processamento) {
                        mestre.addEscravo((CS_Processamento) maq);
                        if (maq instanceof CS_Maquina maqTemp) {
                            maqTemp.addMestre(mestre);
                        }
                    } else if (maq instanceof CS_Switch) {
                        for (final CS_Maquina escr : escravosCluster.get(maq)) {
                            escr.addMestre(mestre);
                            mestre.addEscravo(escr);
                        }
                    }
                }
            }
        }
        //verifica se há usuarios sem nenhum recurso
        final ArrayList<String> proprietarios = new ArrayList<String>();
        final ArrayList<Double> poderComp = new ArrayList<Double>();
        final ArrayList<Double> perfil = new ArrayList<>();
        for (final String user : usuarios.keySet()) {
            proprietarios.add(user);
            poderComp.add(usuarios.get(user));
            perfil.add(perfis.get(user));
        }
        //cria as métricas de usuarios para cada mestre
        for (final CS_Processamento mestre : mestres) {
            final CS_Mestre mst = (CS_Mestre) mestre;
            final MetricasUsuarios mu = new MetricasUsuarios();
            mu.addAllUsuarios(proprietarios, poderComp);
            mst.getEscalonador().setMetricaUsuarios(mu);
        }
        final RedeDeFilas rdf = new RedeDeFilas(mestres, maqs, links, nets);
        //cria as métricas de usuarios globais da rede de filas
        final MetricasUsuarios mu = new MetricasUsuarios();
        mu.addAllUsuarios(proprietarios, poderComp);
        rdf.setUsuarios(proprietarios);
        return rdf;
    }

    public static RedeDeFilasCloud newRedeDeFilasCloud(final Document modelo) {
        final NodeList docmaquinas = modelo.getElementsByTagName("machine");
        final NodeList docclusters = modelo.getElementsByTagName("cluster");
        final NodeList docinternet = modelo.getElementsByTagName("internet");
        final NodeList doclinks = modelo.getElementsByTagName("link");
        final NodeList owners = modelo.getElementsByTagName("owner");
        //---v incluindo as máquinas virtuais
        final NodeList docVMs = modelo.getElementsByTagName("virtualMac");

        final HashMap<Integer, CentroServico> centroDeServicos =
                new HashMap<Integer, CentroServico>();
        final HashMap<CentroServico, List<CS_MaquinaCloud>> escravosCluster =
                new HashMap<CentroServico, List<CS_MaquinaCloud>>();
        final List<CS_Processamento> VMMs = new ArrayList<CS_Processamento>();
        final List<CS_MaquinaCloud> maqs = new ArrayList<CS_MaquinaCloud>();
        final List<CS_VirtualMac> vms = new ArrayList<CS_VirtualMac>();
        final List<CS_Comunicacao> links = new ArrayList<CS_Comunicacao>();
        final List<CS_Internet> nets = new ArrayList<CS_Internet>();
        //cria lista de usuarios e o poder computacional cedido por cada um
        final HashMap<String, Double> usuarios = new HashMap<String, Double>();
        for (int i = 0; i < owners.getLength(); i++) {
            final Element owner = (Element) owners.item(i);
            usuarios.put(owner.getAttribute("id"), 0.0);
        }
        //cria maquinas, mestres, internets e mestres dos clusters
        //Realiza leitura dos icones de máquina
        for (int i = 0; i < docmaquinas.getLength(); i++) {
            final Element maquina = (Element) docmaquinas.item(i);
            final Element id =
                    (Element) maquina.getElementsByTagName("icon_id").item(0);
            final int global = Integer.parseInt(id.getAttribute("global"));
            if (IconicoXML.isValidMaster(maquina)) {
                final Element master = (Element) maquina.getElementsByTagName(
                        "master").item(0);
                final Element carac = (Element) maquina.getElementsByTagName(
                        "characteristic").item(0);
                final Element proc =
                        (Element) carac.getElementsByTagName("process").item(0);
                final Element memoria = (Element) carac.getElementsByTagName(
                        "memory").item(0);
                final Element disco = (Element) carac.getElementsByTagName(
                        "hard_disk").item(0);
                final Element custo =
                        (Element) carac.getElementsByTagName("cost").item(0);
                //instancia o CS_VMM         
                final CS_VMM mestre = new CS_VMM(
                        maquina.getAttribute("id"),
                        maquina.getAttribute("owner"),
                        Double.parseDouble(proc.getAttribute("power")),
                        Double.parseDouble(memoria.getAttribute("size")),
                        Double.parseDouble(disco.getAttribute("size")),
                        Double.parseDouble(maquina.getAttribute("load")),
                        master.getAttribute("scheduler")/*Escalonador*/,
                        master.getAttribute("vm_alloc"));
                VMMs.add(mestre);
                centroDeServicos.put(global, mestre);
                //Contabiliza para o usuario poder computacional do mestre
                usuarios.put(mestre.getProprietario(),
                        usuarios.get(mestre.getProprietario()) + mestre.getPoderComputacional());
            } else {
                //acessa as características do máquina
                final Element caracteristica =
                        (Element) maquina.getElementsByTagName(
                                "characteristic").item(0);
                final Element custo =
                        (Element) caracteristica.getElementsByTagName("cost").item(0);
                final Element processamento =
                        (Element) caracteristica.getElementsByTagName(
                                "process").item(0);
                final Element memoria =
                        (Element) caracteristica.getElementsByTagName("memory"
                        ).item(0);
                final Element disco =
                        (Element) caracteristica.getElementsByTagName(
                                "hard_disk").item(0);
                //instancia um CS_MaquinaCloud
                final CS_MaquinaCloud maq = new CS_MaquinaCloud(
                        maquina.getAttribute("id"),
                        maquina.getAttribute("owner"),
                        Double.parseDouble(processamento.getAttribute("power")),
                        Integer.parseInt(processamento.getAttribute("number")),
                        Double.parseDouble(maquina.getAttribute("load")),
                        Double.parseDouble(memoria.getAttribute("size")),
                        Double.parseDouble(disco.getAttribute("size")),
                        Double.parseDouble(custo.getAttribute("cost_proc")),
                        Double.parseDouble(custo.getAttribute("cost_mem")),
                        Double.parseDouble(custo.getAttribute("cost_disk"))
                );
                maqs.add(maq);
                centroDeServicos.put(global, maq);
                usuarios.put(maq.getProprietario(),
                        usuarios.get(maq.getProprietario()) + maq.getPoderComputacional());
            }
        }
        //Realiza leitura dos icones de cluster
        for (int i = 0; i < docclusters.getLength(); i++) {
            final Element cluster = (Element) docclusters.item(i);
            final Element id =
                    (Element) cluster.getElementsByTagName("icon_id").item(0);
            final Element carac = (Element) cluster.getElementsByTagName(
                    "characteristic").item(0);
            final Element proc =
                    (Element) carac.getElementsByTagName("process").item(0);
            final Element mem =
                    (Element) carac.getElementsByTagName("memory").item(0);
            final Element disc =
                    (Element) carac.getElementsByTagName("hard_disk").item(0);

            final int global = Integer.parseInt(id.getAttribute("global"));
            if (Boolean.parseBoolean(cluster.getAttribute("master"))) {
                final CS_VMM clust = new CS_VMM(
                        cluster.getAttribute("id"),
                        cluster.getAttribute("owner"),
                        Double.parseDouble(proc.getAttribute("power")),
                        Double.parseDouble(mem.getAttribute("size")),
                        Double.parseDouble(disc.getAttribute("size")),
                        0.0,
                        cluster.getAttribute("scheduler")/*Escalonador*/,
                        cluster.getAttribute("vm_alloc"));
                VMMs.add(clust);
                centroDeServicos.put(global, clust);
                //Contabiliza para o usuario poder computacional do mestre
                final int numeroEscravos =
                        Integer.parseInt(cluster.getAttribute(
                                "nodes"));
                final double total =
                        clust.getPoderComputacional() + (clust.getPoderComputacional() * numeroEscravos);
                usuarios.put(clust.getProprietario(),
                        total + usuarios.get(clust.getProprietario()));
                final CS_Switch Switch = new CS_Switch(
                        (cluster.getAttribute("id") + "switch"),
                        Double.parseDouble(cluster.getAttribute("bandwidth")),
                        0.0,
                        Double.parseDouble(cluster.getAttribute("latency")));
                links.add(Switch);
                clust.addConexoesEntrada(Switch);
                clust.addConexoesSaida(Switch);
                Switch.addConexoesEntrada(clust);
                Switch.addConexoesSaida(clust);
                for (int j = 0; j < numeroEscravos; j++) {
                    final Element caracteristica =
                            (Element) cluster.getElementsByTagName(
                                    "characteristic").item(0);
                    final Element custo =
                            (Element) caracteristica.getElementsByTagName(
                                    "cost").item(0);
                    final Element processamento =
                            (Element) caracteristica.getElementsByTagName(
                                    "process").item(0);
                    final Element memoria =
                            (Element) caracteristica.getElementsByTagName(
                                    "memory").item(0);
                    final Element disco =
                            (Element) caracteristica.getElementsByTagName(
                                    "hard_disk").item(0);
                    final CS_MaquinaCloud maq = new CS_MaquinaCloud(
                            (cluster.getAttribute("id") + "." + j),
                            cluster.getAttribute("owner"),
                            Double.parseDouble(processamento.getAttribute(
                                    "power")),
                            Integer.parseInt(processamento.getAttribute(
                                    "number")),
                            Double.parseDouble(memoria.getAttribute("size")),
                            Double.parseDouble(disco.getAttribute("size")),
                            Double.parseDouble(custo.getAttribute("cost_proc")),
                            Double.parseDouble(custo.getAttribute("cost_mem")),
                            Double.parseDouble(custo.getAttribute("cost_disk")),
                            0.0/*TaxaOcupacao*/,
                            j + 1/*identificador da maquina no cluster*/);
                    maq.addConexoesSaida(Switch);
                    maq.addConexoesEntrada(Switch);
                    Switch.addConexoesEntrada(maq);
                    Switch.addConexoesSaida(maq);
                    maq.addMestre(clust);
                    clust.addEscravo(maq);
                    maqs.add(maq);
                    //não adicionei referencia ao switch nem aos escrevos do
                    // cluster aos centros de serviços
                }
            } else {
                final CS_Switch Switch = new CS_Switch(
                        (cluster.getAttribute("id") + "switch"),
                        Double.parseDouble(cluster.getAttribute("bandwidth")),
                        0.0,
                        Double.parseDouble(cluster.getAttribute("latency")));
                links.add(Switch);
                centroDeServicos.put(global, Switch);
                //Contabiliza para o usuario poder computacional do mestre
                final double total = Double.parseDouble(cluster.getAttribute(
                        "power"))
                                     * Integer.parseInt(cluster.getAttribute(
                        "nodes"
                ));
                usuarios.put(cluster.getAttribute("owner"),
                        total + usuarios.get(cluster.getAttribute("owner")));
                final ArrayList<CS_MaquinaCloud> maqTemp =
                        new ArrayList<CS_MaquinaCloud>();
                final int numeroEscravos =
                        Integer.parseInt(cluster.getAttribute(
                                "nodes"));
                for (int j = 0; j < numeroEscravos; j++) {
                    final Element caracteristica =
                            (Element) cluster.getElementsByTagName(
                                    "characteristic");
                    final Element custo =
                            (Element) caracteristica.getElementsByTagName(
                                    "cost");
                    final Element processamento =
                            (Element) caracteristica.getElementsByTagName(
                                    "process");
                    final Element memoria =
                            (Element) caracteristica.getElementsByTagName(
                                    "memory");
                    final Element disco =
                            (Element) caracteristica.getElementsByTagName(
                                    "hard_disk");
                    final CS_MaquinaCloud maq = new CS_MaquinaCloud(
                            (cluster.getAttribute("id") + "." + j),
                            cluster.getAttribute("owner"),
                            Double.parseDouble(processamento.getAttribute(
                                    "power")),
                            Integer.parseInt(processamento.getAttribute(
                                    "number")),
                            Double.parseDouble(memoria.getAttribute("size")),
                            Double.parseDouble(disco.getAttribute("size")),
                            Double.parseDouble(custo.getAttribute("cost_proc")),
                            Double.parseDouble(custo.getAttribute("cost_mem")),
                            Double.parseDouble(custo.getAttribute("cost_disk")),
                            0.0/*TaxaOcupacao*/,
                            j + 1/*identificador da maquina no cluster*/);
                    maq.addConexoesSaida(Switch);
                    maq.addConexoesEntrada(Switch);
                    Switch.addConexoesEntrada(maq);
                    Switch.addConexoesSaida(maq);
                    maqTemp.add(maq);
                    maqs.add(maq);
                }
                escravosCluster.put(Switch, maqTemp);
            }
        }

        //Realiza leitura dos icones de internet
        for (int i = 0; i < docinternet.getLength(); i++) {
            final Element inet = (Element) docinternet.item(i);
            final Element id =
                    (Element) inet.getElementsByTagName("icon_id").item(0);
            final int global = Integer.parseInt(id.getAttribute("global"));
            final CS_Internet net = new CS_Internet(
                    inet.getAttribute("id"),
                    Double.parseDouble(inet.getAttribute("bandwidth")),
                    Double.parseDouble(inet.getAttribute("load")),
                    Double.parseDouble(inet.getAttribute("latency")));
            nets.add(net);
            centroDeServicos.put(global, net);
        }
        //cria os links e realiza a conexão entre os recursos
        for (int i = 0; i < doclinks.getLength(); i++) {
            final Element link = (Element) doclinks.item(i);

            final CS_Link cslink = new CS_Link(
                    link.getAttribute("id"),
                    Double.parseDouble(link.getAttribute("bandwidth")),
                    Double.parseDouble(link.getAttribute("load")),
                    Double.parseDouble(link.getAttribute("latency")));
            links.add(cslink);

            //adiciona entrada e saida desta conexão
            final Element connect =
                    (Element) link.getElementsByTagName("connect").item(0);
            final Vertice origem =
                    (Vertice) centroDeServicos.get(Integer.parseInt(connect.getAttribute("origination")));
            final Vertice destino =
                    (Vertice) centroDeServicos.get(Integer.parseInt(connect.getAttribute("destination")));
            cslink.setConexoesSaida((CentroServico) destino);
            destino.addConexoesEntrada(cslink);
            cslink.setConexoesEntrada((CentroServico) origem);
            origem.addConexoesSaida(cslink);
        }
        //adiciona os escravos aos mestres
        for (int i = 0; i < docmaquinas.getLength(); i++) {
            final Element maquina = (Element) docmaquinas.item(i);
            final Element id =
                    (Element) maquina.getElementsByTagName("icon_id").item(0);
            final int global = Integer.parseInt(id.getAttribute("global"));
            if (IconicoXML.isValidMaster(maquina)) {
                final Element master = (Element) maquina.getElementsByTagName(
                        "master").item(0);
                final NodeList slaves = master.getElementsByTagName("slave");
                final CS_VMM mestre = (CS_VMM) centroDeServicos.get(global);
                for (int j = 0; j < slaves.getLength(); j++) {
                    final Element slave = (Element) slaves.item(j);
                    final CentroServico maq =
                            centroDeServicos.get(Integer.parseInt(slave.getAttribute("id")));
                    if (maq instanceof CS_Processamento) {
                        mestre.addEscravo((CS_Processamento) maq);
                        if (maq instanceof CS_MaquinaCloud maqTemp) {
                            //trecho de debbuging
                            System.out.println(maqTemp.getId() + " adicionou " +
                                               "como mestre: " + mestre.getId());
                            //fim dbg
                            maqTemp.addMestre(mestre);
                        }
                    } else if (maq instanceof CS_Switch) {
                        for (final CS_MaquinaCloud escr :
                                escravosCluster.get(maq)) {
                            escr.addMestre(mestre);
                            mestre.addEscravo(escr);
                        }
                    }
                }
            }
        }

        //Realiza leitura dos ícones de máquina virtual
        for (int i = 0; i < docVMs.getLength(); i++) {
            final Element virtualMac = (Element) docVMs.item(i);
            final CS_VirtualMac VM =
                    new CS_VirtualMac(virtualMac.getAttribute("id"),
                            virtualMac.getAttribute("owner"),
                            Integer.parseInt(virtualMac.getAttribute("power")),
                            Double.parseDouble(virtualMac.getAttribute(
                                    "mem_alloc")),
                            Double.parseDouble(virtualMac.getAttribute(
                                    "disk_alloc")),
                            virtualMac.getAttribute("op_system"));
            //adicionando VMM responsável pela VM
            for (final CS_Processamento aux : VMMs) {
                //System.out.println("id vmm:" + aux.getId());
                //System.out.println("id do vmm na vm:" + virtualMac
                // .getAttribute("vmm") );
                if (virtualMac.getAttribute("vmm").equals(aux.getId())) {
                    //atentar ao fato de que a solução falha se o nome do vmm
                    // for alterado e não atualizado na tabela das vms
                    //To do: corrigir problema futuramente
                    VM.addVMM((CS_VMM) aux);
                    //adicionando VM para o VMM

                    final CS_VMM vmm = (CS_VMM) aux;
                    vmm.addVM(VM);

                }

            }
            vms.add(VM);
        }

        //verifica se há usuarios sem nenhum recurso
        final ArrayList<String> proprietarios = new ArrayList<String>();
        final ArrayList<Double> poderComp = new ArrayList<Double>();
        for (final String user : usuarios.keySet()) {
            proprietarios.add(user);
            poderComp.add(usuarios.get(user));
        }
        //cria as métricas de usuarios para cada mestre
        for (final CS_Processamento mestre : VMMs) {
            final CS_VMM mst = (CS_VMM) mestre;
            final MetricasUsuarios mu = new MetricasUsuarios();
            mu.addAllUsuarios(proprietarios, poderComp);
            mst.getEscalonador().setMetricaUsuarios(mu);
        }
        final RedeDeFilasCloud rdf = new RedeDeFilasCloud(VMMs, maqs, vms,
                links,
                nets);
        //cria as métricas de usuarios globais da rede de filas
        final MetricasUsuarios mu = new MetricasUsuarios();
        mu.addAllUsuarios(proprietarios, poderComp);
        rdf.setUsuarios(proprietarios);
        return rdf;
    }

    /**
     * Obtem a configuração da carga de trabalho contida em um modelo iconico
     *
     * @param modelo contem conteudo recuperado de um arquivo xml
     * @return carga de trabalho contida no modelo
     */
    public static GerarCarga newGerarCarga(final Document modelo) {
        NodeList cargas = modelo.getElementsByTagName("load");
        GerarCarga cargasConfiguracao = null;
        //Realiza leitura da configuração de carga do modelo
        if (cargas.getLength() != 0) {
            final Element cargaAux = (Element) cargas.item(0);
            cargas = cargaAux.getElementsByTagName("random");
            if (cargas.getLength() != 0) {
                final Element carga = (Element) cargas.item(0);
                final int numeroTarefas = Integer.parseInt(carga.getAttribute(
                        "tasks"));
                final int timeOfArrival = Integer.parseInt(carga.getAttribute(
                        "time_arrival"));
                int minComputacao = 0;
                int maxComputacao = 0;
                int AverageComputacao = 0;
                double ProbabilityComputacao = 0;
                int minComunicacao = 0;
                int maxComunicacao = 0;
                int AverageComunicacao = 0;
                double ProbabilityComunicacao = 0;
                final NodeList size = carga.getElementsByTagName("size");
                for (int i = 0; i < size.getLength(); i++) {
                    final Element size1 = (Element) size.item(i);
                    if ("computing".equals(size1.getAttribute("type"))) {
                        minComputacao = Integer.parseInt(size1.getAttribute(
                                "minimum"));
                        maxComputacao = Integer.parseInt(size1.getAttribute(
                                "maximum"));
                        AverageComputacao =
                                Integer.parseInt(size1.getAttribute("average"));
                        ProbabilityComputacao =
                                Double.parseDouble(size1.getAttribute(
                                        "probability"));
                    } else if ("communication".equals(
                            size1.getAttribute("type"))) {
                        minComunicacao = Integer.parseInt(size1.getAttribute(
                                "minimum"));
                        maxComunicacao = Integer.parseInt(size1.getAttribute(
                                "maximum"));
                        AverageComunicacao =
                                Integer.parseInt(size1.getAttribute("average"));
                        ProbabilityComunicacao =
                                Double.parseDouble(size1.getAttribute(
                                        "probability"));
                    }
                }
                cargasConfiguracao = new CargaRandom(numeroTarefas,
                        minComputacao, maxComputacao, AverageComputacao,
                        ProbabilityComputacao, minComunicacao, maxComunicacao
                        , AverageComunicacao, ProbabilityComunicacao,
                        timeOfArrival);
            }
            cargas = cargaAux.getElementsByTagName("node");
            if (cargas.getLength() != 0) {
                final List<CargaForNode> tarefasDoNo =
                        new ArrayList<CargaForNode>();
                for (int i = 0; i < cargas.getLength(); i++) {
                    final Element carga = (Element) cargas.item(i);
                    final String aplicacao = carga.getAttribute("application");
                    final String proprietario = carga.getAttribute("owner");
                    final String escalonador = carga.getAttribute("id_master");
                    final int numeroTarefas =
                            Integer.parseInt(carga.getAttribute(
                                    "tasks"));
                    double minComputacao = 0;
                    double maxComputacao = 0;
                    double minComunicacao = 0;
                    double maxComunicacao = 0;
                    final NodeList size = carga.getElementsByTagName("size");
                    for (int j = 0; j < size.getLength(); j++) {
                        final Element size1 = (Element) size.item(j);
                        if ("computing".equals(size1.getAttribute("type"))) {
                            minComputacao =
                                    Double.parseDouble(size1.getAttribute(
                                            "minimum"));
                            maxComputacao =
                                    Double.parseDouble(size1.getAttribute(
                                            "maximum"));
                        } else if ("communication".equals(
                                size1.getAttribute("type"))) {
                            minComunicacao =
                                    Double.parseDouble(size1.getAttribute(
                                            "minimum"));
                            maxComunicacao =
                                    Double.parseDouble(size1.getAttribute(
                                            "maximum"));
                        }
                    }
                    final CargaForNode item = new CargaForNode(aplicacao,
                            proprietario, escalonador, numeroTarefas,
                            maxComputacao, minComputacao, maxComunicacao,
                            minComunicacao);
                    tarefasDoNo.add(item);
                }
                cargasConfiguracao = new CargaList(tarefasDoNo,
                        GerarCarga.FORNODE);
            }
            cargas = cargaAux.getElementsByTagName("trace");
            if (cargas.getLength() != 0) {
                final Element carga = (Element) cargas.item(0);
                final File filepath = new File(carga.getAttribute("file_path"));
                final Integer num_tarefas = Integer.parseInt(carga.getAttribute(
                        "tasks"));
                final String formato = carga.getAttribute("format");
                if (filepath.exists()) {
                    cargasConfiguracao = new CargaTrace(filepath, num_tarefas
                            , formato);
                }
            }
        }
        return cargasConfiguracao;
    }

    public static void newGrade(final Document descricao,
                                final Set<Vertex> vertices,
                                final Set<Edge> arestas) {
        final HashMap<Integer, Object> icones = new HashMap<Integer, Object>();
        final NodeList maquinas = descricao.getElementsByTagName("machine");
        final NodeList clusters = descricao.getElementsByTagName("cluster");
        final NodeList internet = descricao.getElementsByTagName("internet");
        final NodeList links = descricao.getElementsByTagName("link");
        //Realiza leitura dos icones de cluster
        for (int i = 0; i < clusters.getLength(); i++) {
            final Element cluster = (Element) clusters.item(i);
            final Element pos =
                    (Element) cluster.getElementsByTagName("position").item(0);
            final int x = Integer.parseInt(pos.getAttribute("x"));
            final int y = Integer.parseInt(pos.getAttribute("y"));
            final Element id =
                    (Element) cluster.getElementsByTagName("icon_id").item(0);
            final int global = Integer.parseInt(id.getAttribute("global"));
            final int local = Integer.parseInt(id.getAttribute("local"));
            final Cluster clust = new Cluster(x, y, local, global,
                    Double.parseDouble(cluster.getAttribute("power")));
            clust.setSelected(false);
            vertices.add(clust);
            icones.put(global, clust);
            clust.getId().setName(cluster.getAttribute("id"));
            ValidaValores.addNomeIcone(clust.getId().getName());
            clust.setComputationalPower(Double.parseDouble(cluster.getAttribute("power")));
            IconicoXML.setCaracteristicas(clust, cluster.getElementsByTagName(
                    "characteristic"));
            clust.setSlaveCount(Integer.parseInt(cluster.getAttribute("nodes")));
            clust.setBandwidth(Double.parseDouble(cluster.getAttribute(
                    "bandwidth")));
            clust.setLatency(Double.parseDouble(cluster.getAttribute("latency"
            )));
            clust.setSchedulingAlgorithm(cluster.getAttribute("scheduler"));
            clust.setVmmAllocationPolicy(cluster.getAttribute("vm_alloc"));
            clust.setOwner(cluster.getAttribute("owner"));
            clust.setMaster(Boolean.parseBoolean(cluster.getAttribute("master"
            )));
        }
        //Realiza leitura dos icones de internet
        for (int i = 0; i < internet.getLength(); i++) {
            final Element inet = (Element) internet.item(i);
            final Element pos =
                    (Element) inet.getElementsByTagName("position").item(0);
            final int x = Integer.parseInt(pos.getAttribute("x"));
            final int y = Integer.parseInt(pos.getAttribute("y"));
            final Element id =
                    (Element) inet.getElementsByTagName("icon_id").item(0);
            final int global = Integer.parseInt(id.getAttribute("global"));
            final int local = Integer.parseInt(id.getAttribute("local"));
            final Internet net = new Internet(x, y, local, global);
            net.setSelected(false);
            vertices.add(net);
            icones.put(global, net);
            net.getId().setName(inet.getAttribute("id"));
            ValidaValores.addNomeIcone(net.getId().getName());
            net.setBandwidth(Double.parseDouble(inet.getAttribute("bandwidth")));
            net.setLoadFactor(Double.parseDouble(inet.getAttribute("load")));
            net.setLatency(Double.parseDouble(inet.getAttribute("latency")));
        }
        //Realiza leitura dos icones de máquina
        for (int i = 0; i < maquinas.getLength(); i++) {
            final Element maquina = (Element) maquinas.item(i);
            if (maquina.getElementsByTagName("master").getLength() <= 0) {
                final Element pos = (Element) maquina.getElementsByTagName(
                        "position").item(0);
                final int x = Integer.parseInt(pos.getAttribute("x"));
                final int y = Integer.parseInt(pos.getAttribute("y"));
                final Element id =
                        (Element) maquina.getElementsByTagName("icon_id").item(0);
                final int global = Integer.parseInt(id.getAttribute("global"));
                final int local = Integer.parseInt(id.getAttribute("local"));
                final Machine maq = new Machine(x, y, local, global,
                        Double.parseDouble(maquina.getAttribute("energy")));
                maq.setSelected(false);
                icones.put(global, maq);
                vertices.add(maq);
                maq.getId().setName(maquina.getAttribute("id"));
                ValidaValores.addNomeIcone(maq.getId().getName());
                maq.setComputationalPower(Double.parseDouble(maquina.getAttribute("power")));
                IconicoXML.setCaracteristicas(maq, maquina.getElementsByTagName(
                        "characteristic"));
                maq.setLoadFactor(Double.parseDouble(maquina.getAttribute(
                        "load")));
                maq.setOwner(maquina.getAttribute("owner"));
            } else {
                final Element pos = (Element) maquina.getElementsByTagName(
                        "position").item(0);
                final int x = Integer.parseInt(pos.getAttribute("x"));
                final int y = Integer.parseInt(pos.getAttribute("y"));
                final Element id =
                        (Element) maquina.getElementsByTagName("icon_id").item(0);
                final int global = Integer.parseInt(id.getAttribute("global"));
                final int local = Integer.parseInt(id.getAttribute("local"));
                final Machine maq = new Machine(x, y, local, global,
                        Double.parseDouble(maquina.getAttribute("energy")));
                maq.setSelected(false);
                icones.put(global, maq);
            }
        }
        //Realiza leitura dos mestres
        for (int i = 0; i < maquinas.getLength(); i++) {
            final Element maquina = (Element) maquinas.item(i);
            if (IconicoXML.isValidMaster(maquina)) {
                final Element id =
                        (Element) maquina.getElementsByTagName("icon_id").item(0);
                final int global = Integer.parseInt(id.getAttribute("global"));
                final Machine maq = (Machine) icones.get(global);
                vertices.add(maq);
                maq.getId().setName(maquina.getAttribute("id"));
                ValidaValores.addNomeIcone(maq.getId().getName());
                maq.setComputationalPower(Double.parseDouble(maquina.getAttribute("power")));
                IconicoXML.setCaracteristicas(maq, maquina.getElementsByTagName(
                        "characteristic"));
                maq.setLoadFactor(Double.parseDouble(maquina.getAttribute(
                        "load")));
                maq.setOwner(maquina.getAttribute("owner"));
                final Element master = (Element) maquina.getElementsByTagName(
                        "master").item(0);
                maq.setSchedulingAlgorithm(master.getAttribute("scheduler"));
                maq.setVmmAllocationPolicy(master.getAttribute("vm_alloc"));
                maq.setMaster(true);
                final NodeList slaves = master.getElementsByTagName("slave");
                final List<GridItem> escravos =
                        new ArrayList<GridItem>(slaves.getLength());
                for (int j = 0; j < slaves.getLength(); j++) {
                    final Element slave = (Element) slaves.item(j);
                    final GridItem escravo =
                            (GridItem) icones.get(Integer.parseInt(slave.getAttribute("id")));
                    if (escravo != null) {
                        escravos.add(escravo);
                    }
                }
                maq.setSlaves(escravos);
            }
        }
        //Realiza leitura dos icones de rede
        for (int i = 0; i < links.getLength(); i++) {
            final Element link = (Element) links.item(i);
            final Element id =
                    (Element) link.getElementsByTagName("icon_id").item(0);
            final int global = Integer.parseInt(id.getAttribute("global"));
            final int local = Integer.parseInt(id.getAttribute("local"));
            final int x = 0;
            final int y = 0;
            final int px = 0;
            final int py = 0;
            final Element connect =
                    (Element) link.getElementsByTagName("connect").item(0);
            final Vertex origem =
                    (Vertex) icones.get(Integer.parseInt(connect.getAttribute("origination")));
            final Vertex destino =
                    (Vertex) icones.get(Integer.parseInt(connect.getAttribute("destination")));
            final Link lk = new Link(origem, destino, local, global);
            lk.setSelected(false);
            ((GridItem) origem).getOutboundConnections().add(lk);
            ((GridItem) destino).getInboundConnections().add(lk);
            arestas.add(lk);
            lk.getId().setName(link.getAttribute("id"));
            ValidaValores.addNomeIcone(lk.getId().getName());
            lk.setBandwidth(Double.parseDouble(link.getAttribute("bandwidth")));
            lk.setLoadFactor(Double.parseDouble(link.getAttribute("load")));
            lk.setLatency(Double.parseDouble(link.getAttribute("latency")));
        }
    }

    private static void setCaracteristicas(final GridItem item,
                                           final NodeList elementsByTagName) {
        Machine maq = null;
        Cluster clust = null;
        if (item instanceof Machine) {
            maq = (Machine) item;
        } else if (item instanceof Cluster) {
            clust = (Cluster) item;
        }
        if (elementsByTagName.getLength() > 0 && clust != null) {
            final Element caracteristicas = (Element) elementsByTagName.item(0);
            final Element process =
                    (Element) caracteristicas.getElementsByTagName(
                            "process").item(0);
            clust.setComputationalPower(Double.valueOf(process.getAttribute(
                    "power")));
            clust.setCoreCount(Integer.valueOf(process.getAttribute("number")));
            final Element memory =
                    (Element) caracteristicas.getElementsByTagName(
                            "memory").item(0);
            clust.setRam(Double.valueOf(memory.getAttribute("size")));
            final Element disk = (Element) caracteristicas.getElementsByTagName(
                    "hard_disk").item(0);
            clust.setHardDisk(Double.valueOf(disk.getAttribute("size")));
            if (caracteristicas.getElementsByTagName("cost").getLength() > 0) {
                final Element cost =
                        (Element) caracteristicas.getElementsByTagName("cost").item(0);
                clust.setCostPerProcessing(Double.valueOf(cost.getAttribute(
                        "cost_proc")));
                clust.setCostPerMemory(Double.valueOf(cost.getAttribute(
                        "cost_mem")));
                clust.setCostPerDisk(Double.valueOf(cost.getAttribute(
                        "cost_disk")));
            }
        } else if (elementsByTagName.getLength() > 0 && maq != null) {
            final Element caracteristicas = (Element) elementsByTagName.item(0);
            final Element process =
                    (Element) caracteristicas.getElementsByTagName(
                            "process").item(0);
            maq.setComputationalPower(Double.valueOf(process.getAttribute(
                    "power")));
            maq.setCoreCount(Integer.valueOf(process.getAttribute("number")));
            final Element memory =
                    (Element) caracteristicas.getElementsByTagName(
                            "memory").item(0);
            maq.setRam(Double.valueOf(memory.getAttribute("size")));
            final Element disk = (Element) caracteristicas.getElementsByTagName(
                    "hard_disk").item(0);
            maq.setHardDisk(Double.valueOf(disk.getAttribute("size")));
            if (caracteristicas.getElementsByTagName("cost").getLength() > 0) {
                final Element cost =
                        (Element) caracteristicas.getElementsByTagName("cost").item(0);
                maq.setCostPerProcessing(Double.valueOf(cost.getAttribute(
                        "cost_proc")));
                maq.setCostPerMemory(Double.valueOf(cost.getAttribute(
                        "cost_mem")));
                maq.setCostPerDisk(Double.valueOf(cost.getAttribute(
                        "cost_disk")));
            }

        }
    }

    public static HashSet<String> newSetUsers(final Document descricao) {
        final NodeList owners = descricao.getElementsByTagName("owner");
        final HashSet<String> usuarios = new HashSet<String>();
        //Realiza leitura dos usuários/proprietários do modelo
        for (int i = 0; i < owners.getLength(); i++) {
            final Element owner = (Element) owners.item(i);
            usuarios.add(owner.getAttribute("id"));
        }
        return usuarios;
    }

    public static List<String> newListUsers(final Document descricao) {
        final NodeList owners = descricao.getElementsByTagName("owner");
        final List<String> usuarios = new ArrayList<String>();
        //Realiza leitura dos usuários/proprietários do modelo
        for (int i = 0; i < owners.getLength(); i++) {
            final Element owner = (Element) owners.item(i);
            usuarios.add(owner.getAttribute("id"));
        }
        return usuarios;
    }

    public static HashSet<VirtualMachine> newListVirtualMachines(final Document descricao) {
        final NodeList owners = descricao.getElementsByTagName("virtualMac");
        final HashSet<VirtualMachine> maqVirtuais =
                new HashSet<VirtualMachine>();
        for (int i = 0; i < owners.getLength(); i++) {
            final Element owner = (Element) owners.item(i);
            final VirtualMachine mac = new VirtualMachine(owner.getAttribute(
                    "id"),
                    owner.getAttribute("owner"),
                    owner.getAttribute("vmm"),
                    Integer.parseInt(owner.getAttribute("power")),
                    Double.parseDouble(owner.getAttribute("mem_alloc")),
                    Double.parseDouble(owner.getAttribute("disk_alloc")),
                    owner.getAttribute("op_system"));
            maqVirtuais.add(mac);
        }
        return maqVirtuais;
    }

    public static Document[] clone(final File file, final int number) throws ParserConfigurationException, IOException, SAXException {
        final Document[] documento = new Document[number];
        final DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(true);
        final DocumentBuilder builder = factory.newDocumentBuilder();
        //Indicar local do arquivo .dtd
        for (int i = 0; i < number; i++) {
            builder.setEntityResolver(new EntityResolver() {
                final InputSource substitute =
                        new InputSource(IconicoXML.class.getResourceAsStream(
                                "iSPD.dtd"));

                public InputSource resolveEntity(final String publicId,
                                                 final String systemId) throws SAXException, IOException {
                    return this.substitute;
                }
            });
            documento[i] = builder.parse(file);
        }
        //inputStream.close();
        return documento;
    }


    public static HashMap<String, Double> newListPerfil(final Document doc) {
        final NodeList owners = doc.getElementsByTagName("owner");
        final var perfis = new HashMap<String, Double>(owners.getLength());
        //Realiza leitura dos usuários/proprietários do modelo
        for (int i = 0; i < owners.getLength(); i++) {
            final Element owner = (Element) owners.item(i);
            perfis.put(owner.getAttribute("id"),
                    Double.parseDouble(owner.getAttribute("powerlimit")));
        }
        return perfis;
    }

    public void addUsers(final Collection<String> users,
                         final Map<String, Double> limits) {
        // TODO: Iterate over the HashMap instead?
        users.stream()
                .map(user -> this.anElement("owner",
                        "id", user,
                        "powerlimit", limits.get(user)
                ))
                .forEach(this.system::appendChild);
    }

    private Element anElement(
            final String name,
            final String k1, final Object v1,
            final String k2, final Object v2) {
        return this.anElement(name, new Object[][] {
                { k1, v1 },
                { k2, v2 },
        });
    }

    private Element anElement(
            final String name, final Object[][] attrs) {
        return this.anElement(name, attrs, IconicoXML.NO_CHILDREN);
    }

    private Element anElement(
            final String name, final Object[][] attrs,
            final Node[] children) {
        final var e = this.doc.createElement(name);

        for (final var attr : attrs) {
            final var key = attr[0];
            final var value = attr[1];
            e.setAttribute((String) key, value.toString());
        }

        Arrays.stream(children)
                .forEach(e::appendChild);

        return e;
    }

    public void addInternet(final int x, final int y, final int idLocal,
                            final int idGlobal,
                            final String nome,
                            final double banda, final double ocupacao,
                            final double latencia) {
        final Element aux;
        final Element posicao = this.aPositionElement(x, y);
        final Element icon_id = this.anIconIdElement(idGlobal, idLocal);

        aux = this.doc.createElement("internet");
        aux.setAttribute("bandwidth", Double.toString(banda));
        aux.setAttribute("load", Double.toString(ocupacao));
        aux.setAttribute("latency", Double.toString(latencia));

        aux.setAttribute("id", nome);
        aux.appendChild(posicao);
        aux.appendChild(icon_id);
        this.system.appendChild(aux);
    }

    public void addCluster(
            final Integer x, final Integer y,
            final Integer localId, final Integer globalId, final String name,
            final Integer slaveCount,
            final Double power, final Integer coreCount,
            final Double memory, final Double disk,
            final Double bandwidth, final Double latency,
            final String scheduler,
            final String owner,
            final Boolean isMaster) {
        this.system.appendChild(this.anElement(
                "cluster", new Object[][] {
                        { "nodes", slaveCount },
                        { "power", power },
                        { "bandwidth", bandwidth },
                        { "latency", latency },
                        { "scheduler", scheduler },
                        { "owner", owner },
                        { "master", isMaster },
                        { "id", name },
                }, new Node[] {
                        this.aPositionElement(x, y),
                        this.anIconIdElement(globalId, localId),
                        this.newCharacteristic(power, coreCount, memory, disk,
                                0.0, 0.0, 0.0),
                }
        ));
    }

    public void addClusterIaaS(final Integer x, final Integer y,
                               final Integer idLocal,
                               final Integer idGlobal, final String nome,
                               final Integer numeroEscravos,
                               final Double poderComputacional,
                               final Integer numeroNucleos,
                               final Double memoriaRAM,
                               final Double discoRigido,
                               final Double banda, final Double latencia,
                               final String algoritmo, final String alloc,
                               final Double CostperProcessing,
                               final Double Costpermemory,
                               final Double CostperDisk,
                               final String proprietario,
                               final Boolean mestre) {
        final Element aux;
        final Element posicao = this.doc.createElement("position");
        posicao.setAttribute("x", x.toString());
        posicao.setAttribute("y", y.toString());
        final Element icon_id = this.anIconIdElement(idGlobal, idLocal);

        aux = this.doc.createElement("cluster");
        aux.setAttribute("nodes", numeroEscravos.toString());
        aux.setAttribute("power", poderComputacional.toString());
        aux.setAttribute("bandwidth", banda.toString());
        aux.setAttribute("latency", latencia.toString());
        aux.setAttribute("scheduler", algoritmo);
        aux.setAttribute("vm_alloc", alloc);
        aux.setAttribute("owner", proprietario);
        aux.setAttribute("master", mestre.toString());

        aux.setAttribute("id", nome);
        aux.appendChild(posicao);
        aux.appendChild(icon_id);
        aux.appendChild(this.newCharacteristic(poderComputacional,
                numeroNucleos,
                memoriaRAM, discoRigido,
                CostperProcessing, Costpermemory, CostperDisk));
        this.system.appendChild(aux);
    }

    private Element anIconIdElement(final int global, final int local) {
        return this.anElement("icon_id", "global", global, "local", local);
    }

    private Node newCharacteristic(final Double poderComputacional,
                                   final Integer numeroNucleos,
                                   final Double memoriaRAM,
                                   final Double discoRigido,
                                   final Double costperProcessing,
                                   final Double costperMemory,
                                   final Double costperDisk) {
        final Element characteristic = this.doc.createElement("characteristic");
        final Element process = this.doc.createElement("process");
        process.setAttribute("power", poderComputacional.toString());
        process.setAttribute("number", numeroNucleos.toString());
        final Element memory = this.doc.createElement("memory");
        memory.setAttribute("size", memoriaRAM.toString());
        final Element hard_disk = this.doc.createElement("hard_disk");
        hard_disk.setAttribute("size", discoRigido.toString());
        final Element cost = this.doc.createElement("cost");
        cost.setAttribute("cost_proc", costperProcessing.toString());
        final Element cost_mem = this.doc.createElement("cost_mem");
        cost.setAttribute("cost_mem", costperMemory.toString());
        final Element cost_disk = this.doc.createElement("cost_disk");
        cost.setAttribute("cost_disk", costperDisk.toString());

        characteristic.appendChild(process);
        characteristic.appendChild(memory);
        characteristic.appendChild(hard_disk);
        characteristic.appendChild(cost);
        return characteristic;
    }

    public void addMachine(
            final Integer x, final Integer y,
            final Integer idLocal, final Integer idGlobal, final String nome,
            final Double poderComputacional, final Double ocupacao,
            final String algoritmo, final String proprietario,
            final Integer numeroNucleos, final Double memory, final Double disk,
            final boolean mestre, final Collection<Integer> escravos,
            final Double energy) {
        final Element aux;
        final Element posicao = this.doc.createElement("position");
        posicao.setAttribute("x", x.toString());
        posicao.setAttribute("y", y.toString());
        final Element icon_id = this.anIconIdElement(idGlobal, idLocal);

        aux = this.doc.createElement("machine");
        aux.setAttribute("power", Double.toString(poderComputacional));
        aux.setAttribute("load", Double.toString(ocupacao));
        aux.setAttribute("owner", proprietario);
        aux.setAttribute("energy", Double.toString(energy));
        if (mestre) {
            //preenche escravos
            final Element master = this.doc.createElement("master");
            master.setAttribute("scheduler", algoritmo);
            for (final Integer escravo : escravos) {
                final Element slave = this.doc.createElement("slave");
                slave.setAttribute("id", escravo.toString());
                master.appendChild(slave);
            }
            aux.appendChild(master);
        }
        aux.setAttribute("id", nome);
        aux.appendChild(posicao);
        aux.appendChild(icon_id);
        aux.appendChild(this.newCharacteristic(poderComputacional,
                numeroNucleos,
                memory, disk));
        this.system.appendChild(aux);
    }

    private Node newCharacteristic(final Double power, final Integer coreCount,
                                   final Double memory, final Double disk) {
        return this.anElement(
                "characteristic", IconicoXML.NO_ATTRS, new Element[] {
                        this.anElement("process",
                                "power", power,
                                "number", coreCount
                        ),
                        this.anElement("memory", "size", memory),
                        this.anElement("hard_disk", "size", disk),
                });
    }

    private Element anElement(
            final String name, final String key, final Object value) {
        return this.anElement(name, new Object[][] {
                { key, value },
        });
    }

    public void addMachine(
            final Integer x, final Integer y,
            final Integer localId, final Integer globalId, final String name,
            final Double power, final Double occupancy,
            final String scheduler, final String owner,
            final Integer coreCount, final Double memory, final Double disk,
            final boolean isMaster, final Collection<Integer> slaves) {
        this.addMachineInner(x, y, localId, globalId, name,
                power, occupancy, scheduler, owner, coreCount, memory, disk,
                0.0, 0.0, 0.0,
                isMaster, slaves, IconicoXML.NO_ATTRS, IconicoXML.NO_ATTRS);
    }

    public void addMachineIaaS(
            final Integer x, final Integer y,
            final Integer localId, final Integer globalId, final String name,
            final Double power, final Double occupancy,
            final String vmAlloc, final String scheduler, final String owner,
            final Integer coreCount, final Double memory, final Double disk,
            final Double costPerProcessing,
            final Double costPerMemory,
            final Double costPerDisk,
            final boolean isMaster, final Collection<Integer> slaves) {
        this.addMachineInner(x, y, localId, globalId, name,
                power, occupancy, scheduler, owner, coreCount, memory, disk,
                costPerProcessing, costPerMemory, costPerDisk, isMaster, slaves,
                IconicoXML.NO_ATTRS, new Object[][] { { "vm_alloc", vmAlloc }, }
        );
    }

    private void addMachineInner(
            final Integer x, final Integer y,
            final Integer localId, final Integer globalId, final String name,
            final Double power, final Double occupancy,
            final String scheduler, final String owner,
            final Integer coreCount, final Double memory, final Double disk,
            final Double costPerProcessing,
            final Double costPerMemory,
            final Double costPerDisk,
            final boolean isMaster, final Collection<Integer> slaves,
            final Object[][] extraAttrs, final Object[][] extraMasterAttrs) {
        final var attrs = Arrays.asList(new Object[][] {
                { "id", name },
                { "power", power },
                { "load", occupancy },
                { "owner", owner },
        });

        attrs.addAll(Arrays.asList(extraAttrs));

        final var machine = this.anElement(
                "machine", attrs.toArray(Object[][]::new), new Node[] {
                        this.aPositionElement(x, y),
                        this.anIconIdElement(globalId, localId),
                        this.newCharacteristic(
                                power, coreCount, memory, disk,
                                costPerProcessing, costPerMemory, costPerDisk
                        ),

                }
        );

        if (isMaster) {
            machine.appendChild(this.aMasterElement(
                    scheduler, slaves, extraMasterAttrs
            ));
        }

        this.system.appendChild(machine);
    }

    private Element aMasterElement(final String scheduler,
                                   final Collection<Integer> slaves,
                                   final Object[][] extraAttrs) {
        final var attrs = Arrays.asList(new Object[][] {
                { "scheduler", scheduler },
        });

        attrs.addAll(Arrays.asList(extraAttrs));

        return this.anElement(
                "master", attrs.toArray(Object[][]::new),
                slaves.stream()
                        .map(this::aSlaveElement)
                        .toArray(Element[]::new)
        );
    }

    private Element aSlaveElement(final Integer id) {
        return this.anElement("slave", "id", id);
    }

    public void addLink(
            final int x0, final int y0,
            final int x1, final int y1,
            final int localId, final int globalId,
            final String name, final double bandwidth,
            final double linkLoad, final double latency,
            final int origination, final int destination) {
        // TODO: During refactoring steps were reordered. Need to test.
        this.system.appendChild(this.anElement(
                "link", new Object[][] {
                        { "id", name },
                        { "bandwidth", bandwidth },
                        { "load", linkLoad },
                        { "latency", latency },
                }, new Element[] {
                        this.anElement("connect",
                                "origination", origination,
                                "destination", destination),
                        this.aPositionElement(x0, y0),
                        this.aPositionElement(x1, y1),
                        this.anIconIdElement(globalId, localId),
                }
        ));
    }

    private Element aPositionElement(final int x, final int y) {
        return this.anElement("position", "x", x, "y", y);
    }

    public void addVirtualMachines(
            final String id, final String owner, final String vmm,
            final int power, final double memory, final double disk,
            final String os) {
        this.system.appendChild(this.anElement(
                "virtualMac", new Object[][] {
                        { "id", id },
                        { "owner", owner },
                        { "vmm", vmm },
                        { "power", power },
                        { "mem_alloc", memory },
                        { "disk_alloc", disk },
                        { "op_system", os },
                }
        ));
    }

    public void setLoadRandom(
            final Integer taskCount, final Integer arrivalTime,
            final Integer compMax, final Integer compAvg,
            final Integer compMin, final Double compProb,
            final Integer commMax, final Integer commAvg,
            final Integer commMin, final Double commProb) {
        this.addElementToLoad(this.anElement(
                "random", new Object[][] {
                        { "tasks", taskCount },
                        { "time_arrival", arrivalTime },
                }, new Element[] {
                        this.anElement("size", new Object[][] {
                                { "type", "computing" },
                                { "maximum", compMax },
                                { "average", compAvg },
                                { "minimum", compMin },
                                { "probability", compProb },
                        }),
                        this.anElement("size", new Object[][] {
                                { "type", "communication" },
                                { "maximum", commMax },
                                { "average", commAvg },
                                { "minimum", commMin },
                                { "probability", commProb },
                        }),
                }
        ));
    }

    private void addElementToLoad(final Node elem) {
        this.createLoadIfNull();
        this.load.appendChild(elem);
    }

    private void createLoadIfNull() {
        if (this.load == null) {
            this.load = this.doc.createElement("load");
            this.system.appendChild(this.load);
        }
    }

    public void addLoadNo(
            final String application,
            final String owner,
            final String masterId,
            final Integer taskCount,
            final Double maxComp, final Double minComp,
            final Double maxComm, final Double minComm) {
        this.addElementToLoad(this.anElement(
                "node", new Object[][] {
                        { "application", application },
                        { "owner", owner },
                        { "id_master", masterId },
                        { "tasks", taskCount },
                }, new Element[] {
                        this.anElement("size", new Object[][] {
                                { "type", "computing" },
                                { "maximum", maxComp },
                                { "minimum", minComp },
                        }),
                        this.anElement("size", new Object[][] {
                                { "type", "communication" },
                                { "maximum", maxComm },
                                { "minimum", minComm },
                        }),
                }
        ));
    }

    public void setLoadTrace(
            final String file, final String tasks, final String format) {
        this.addElementToLoad(this.anElement(
                "trace", new String[][] {
                        { "file_path", file },
                        { "tasks", tasks },
                        { "format", format },
                }
        ));
    }

    public Document getDescricao() {
        return this.doc;
    }
}
