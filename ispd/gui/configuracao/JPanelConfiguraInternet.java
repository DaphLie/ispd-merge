/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * JPanelConfiguraInternet.java
 *
 * Created on 02/03/2011, 10:33:43
 */
package ispd.gui.configuracao;

import ispd.gui.Icone;
import ispd.ValidaValores;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 *
 * @author denison_usuario
 */
public class JPanelConfiguraInternet extends javax.swing.JPanel {

    private Icone icone;
    private ResourceBundle palavras;

    /** Creates new form JPanelConfiguraInternet */
    public JPanelConfiguraInternet() {
        Locale locale = Locale.getDefault();
        palavras = ResourceBundle.getBundle("ispd.idioma.Idioma", locale);
        initComponents();
        jTableDouble.getColumnModel().getColumn(0).setPreferredWidth(100);
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jLabelTitle = new javax.swing.JLabel();
        jLabelInicial = new javax.swing.JLabel();
        jTableString = new javax.swing.JTable();
        jTableDouble = new javax.swing.JTable();
        jLabel = new javax.swing.JLabel();

        jLabelTitle.setFont(new java.awt.Font("Tahoma", 1, 12));
        jLabelTitle.setText(palavras.getString("Internet icon configuration")); // NOI18N

        jLabelInicial.setText(palavras.getString("Configuration for the icon") + "#: " + "0");

        jTableString.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {"Label:", "nome"}
            },
            new String [] {
                "", ""
            }
        ) {
            Class[] types = new Class [] {
                java.lang.String.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean [] {
                false, true
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        jTableString.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_OFF);
        jTableString.setSelectionBackground(new java.awt.Color(255, 255, 255));
        jTableString.setSelectionForeground(new java.awt.Color(0, 0, 0));
        jTableString.getTableHeader().setResizingAllowed(false);
        jTableString.getTableHeader().setReorderingAllowed(false);
        jTableString.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                jTableStringPropertyChange(evt);
            }
        });

        jTableDouble.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {"Bandwidth:", null},
                {"Load Factor:", null},
                {"Latency:", null}
            },
            new String [] {
                "", ""
            }
        ) {
            Class[] types = new Class [] {
                java.lang.String.class, java.lang.Double.class
            };
            boolean[] canEdit = new boolean [] {
                false, true
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        jTableDouble.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_OFF);
        jTableDouble.setSelectionBackground(new java.awt.Color(255, 255, 255));
        jTableDouble.setSelectionForeground(new java.awt.Color(0, 0, 0));
        jTableDouble.getTableHeader().setResizingAllowed(false);
        jTableDouble.getTableHeader().setReorderingAllowed(false);
        jTableDouble.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                jTableDoublePropertyChange(evt);
            }
        });

        jLabel.setText("<html>Mb/s<br>%<br>s</html>");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jLabelInicial)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(jTableString, javax.swing.GroupLayout.Alignment.LEADING, 0, 0, Short.MAX_VALUE)
                    .addComponent(jTableDouble, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 139, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addComponent(jLabelTitle)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jLabelTitle)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabelInicial)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jTableString, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jTableDouble, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 48, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void jTableStringPropertyChange(java.beans.PropertyChangeEvent evt) {//GEN-FIRST:event_jTableStringPropertyChange
        // TODO add your handling code here:
        if (jTableString.getValueAt(0, 1) != null && !icone.getNome().equals(jTableString.getValueAt(0, 1).toString())) {
            if (ValidaValores.NomeIconeNaoExiste(jTableString.getValueAt(0, 1).toString()) && ValidaValores.validaNomeIcone(jTableString.getValueAt(0, 1).toString())) {
                ValidaValores.removeNomeIcone(icone.getNome());
                icone.setNome(jTableString.getValueAt(0, 1).toString());
                ValidaValores.addNomeIcone(jTableString.getValueAt(0, 1).toString());
            } else {
                jTableString.setValueAt(icone.getNome(), 0, 1);
            }
        } else {
            setIcone(icone);
        }
    }//GEN-LAST:event_jTableStringPropertyChange

    private void jTableDoublePropertyChange(java.beans.PropertyChangeEvent evt) {//GEN-FIRST:event_jTableDoublePropertyChange
        // TODO add your handling code here:
        switch (jTableDouble.getSelectedRow()) {
            case 0:
                if (jTableDouble.getValueAt(0, 1) != null && ValidaValores.validaDouble(jTableDouble.getValueAt(0, 1).toString())) {
                    icone.setBanda((Double) jTableDouble.getValueAt(0, 1));
                }
                break;
            case 1:
                if (jTableDouble.getValueAt(1, 1) != null && ValidaValores.validaDouble(jTableDouble.getValueAt(1, 1).toString())) {
                    icone.setTaxaOcupacao((Double) jTableDouble.getValueAt(1, 1));
                }
                break;
            case 2:
                if (jTableDouble.getValueAt(2, 1) != null && ValidaValores.validaDouble(jTableDouble.getValueAt(2, 1).toString())) {
                    icone.setLatencia((Double) jTableDouble.getValueAt(2, 1));
                }
                break;
        }
        setIcone(icone);
    }//GEN-LAST:event_jTableDoublePropertyChange
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel jLabel;
    private javax.swing.JLabel jLabelInicial;
    private javax.swing.JLabel jLabelTitle;
    private javax.swing.JTable jTableDouble;
    private javax.swing.JTable jTableString;
    // End of variables declaration//GEN-END:variables

    public void setIcone(Icone icone) {
        this.icone = icone;
        this.jLabelInicial.setText(palavras.getString("Configuration for the icon") + "#: " + String.valueOf(icone.getIdGlobal()));
        jTableString.setValueAt(icone.getNome(), 0, 1);
        jTableDouble.setValueAt(icone.getBanda(), 0, 1);
        jTableDouble.setValueAt(icone.getTaxaOcupacao(), 1, 1);
        jTableDouble.setValueAt(icone.getLatencia(), 2, 1);
    }

    public void setIdioma(ResourceBundle palavras) {
        this.palavras = palavras;
        initTexts();
    }

    private void initTexts() {
        jLabelTitle.setText(palavras.getString("Internet icon configuration"));
        if (icone == null) {
            jLabelInicial.setText(palavras.getString("Configuration for the icon") + "#: 0");
        } else {
            jLabelInicial.setText(palavras.getString("Configuration for the icon") + "#: " + String.valueOf(icone.getIdGlobal()));
        }
        jTableString.setValueAt(palavras.getString("Label:"), 0, 0);
        jTableDouble.setValueAt(palavras.getString("Bandwidth:"), 0, 0);
        jTableDouble.setValueAt(palavras.getString("Load Factor:"), 1, 0);
        jTableDouble.setValueAt(palavras.getString("Latency:"), 2, 0);
    }
}
