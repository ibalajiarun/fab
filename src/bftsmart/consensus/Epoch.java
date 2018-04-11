/**
Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package bftsmart.consensus;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.codec.binary.Base64;

import bftsmart.consensus.messages.ConsensusMessage;
import bftsmart.reconfiguration.ServerViewController;
import bftsmart.reconfiguration.views.View;
import bftsmart.tom.core.messages.TOMMessage;


/**
 * This class stands for a consensus epoch, as described in
 * Cachin's 'Yet Another Visit to Paxos' (April 2011)
 */
public class Epoch implements Serializable {

    private static final long serialVersionUID = -2891450035863688295L;
    private final transient Consensus consensus; // Consensus where the epoch belongs to
    
    private final int timestamp; // Epochs's timestamp
    private final int me; // Process ID
    private boolean[] acceptSetted;
    private boolean[] commitProofSetted;
    private byte[][] accept; // ACCEPT values from other processes
    private byte[][] commitProof; // accepted values from other processes
    
    private boolean alreadyRemoved = false; // indicates if this epoch was removed from its consensus

    public byte[] propValue = null; // proposed value
    public TOMMessage[] deserializedPropValue = null; //utility var
    public byte[] propValueHash = null; // proposed value hash
    public HashSet<ConsensusMessage> proof; // proof from other processes

    private View lastView = null;

    private ServerViewController controller;

    /**
     * Creates a new instance of Epoch for acceptors
     * @param controller
     * @param parent Consensus to which this epoch belongs
     * @param timestamp Timestamp of the epoch
     */
    public Epoch(ServerViewController controller, Consensus parent, int timestamp) {
        this.consensus = parent;
        this.timestamp = timestamp;
        this.controller = controller;
        this.proof = new HashSet<>();
        //ExecutionManager manager = consensus.getManager();

        this.lastView = controller.getCurrentView();
        this.me = controller.getStaticConf().getProcessId();

        //int[] acceptors = manager.getAcceptors();
        int n = controller.getCurrentViewN();

        acceptSetted = new boolean[n];
        commitProofSetted = new boolean[n];

        Arrays.fill(acceptSetted, false);
        Arrays.fill(commitProofSetted, false);

        if (timestamp == 0) {
            this.accept = new byte[n][];
            this.commitProof = new byte[n][];

            Arrays.fill((Object[]) accept, null);
            Arrays.fill((Object[]) commitProof, null);
        } else {
            Epoch previousEpoch = consensus.getEpoch(timestamp - 1, controller);

            this.accept = previousEpoch.getCommitProof();
            this.commitProof = previousEpoch.getCommitProof();
        }
    }

    // If a view change takes place and concurrentely this consensus is still
    // receiving messages, the accept and commitProof arrays must be updated
    private void updateArrays() {
        
        if (lastView.getId() != controller.getCurrentViewId()) {
            
            int n = controller.getCurrentViewN();
            
            byte[][] accept = new byte[n][];
            byte[][] commitProof = new byte[n][];
            
            boolean[] acceptSetted = new boolean[n];
            boolean[] commitProofSetted = new boolean[n];

            Arrays.fill(acceptSetted, false);
            Arrays.fill(commitProofSetted, false);
        
            for (int pid : lastView.getProcesses()) {
                
                if (controller.isCurrentViewMember(pid)) {
                    
                    int currentPos = controller.getCurrentViewPos(pid);
                    int lastPos = lastView.getPos(pid);
                    
                    accept[currentPos] = this.accept[lastPos];
                    commitProof[currentPos] = this.commitProof[lastPos];

                    acceptSetted[currentPos] = this.acceptSetted[lastPos];
                    commitProofSetted[currentPos] = this.commitProofSetted[lastPos];

                }
            }
            
            this.accept = accept;
            this.commitProof = commitProof;

            this.acceptSetted = acceptSetted;
            this.commitProofSetted = commitProofSetted;

            lastView = controller.getCurrentView();
            
        }
    }
            
    /**
     * Set this epoch as removed from its consensus instance
     */
    public void setRemoved() {
        this.alreadyRemoved = true;
    }

    /**
     * Informs if this epoch was removed from its consensus instance
     * @return True if it is removed, false otherwise
     */
    public boolean isRemoved() {
        return this.alreadyRemoved;
    }


    public void addToProof(ConsensusMessage pm) {
        proof.add(pm);
    }
    
    public Set<ConsensusMessage> getProof() {
        return proof;
    }
    /**
     * Retrieves the duration for the timeout
     * @return Duration for the timeout
     */
    /*public long getTimeout() {
        return this.timeout;
    }*/

    /**
     * Retrieves this epoch's timestamp
     * @return This epoch's timestamp
     */
    public int getTimestamp() {
        return timestamp;
    }

    /**
     * Retrieves this epoch's consensus
     * @return This epoch's consensus
     */
    public Consensus getConsensus() {
        return consensus;
    }

    /**
     * Informs if there is a ACCEPT value from a replica
     * @param acceptor The replica ID
     * @return True if there is a ACCEPT value from a replica, false otherwise
     */
    public boolean isAcceptSetted(int acceptor) {
        
        updateArrays();
        
        //******* EDUARDO BEGIN **************//
        int p = this.controller.getCurrentViewPos(acceptor);
        if(p >= 0){
            return accept[p] != null;
        }else{
            return false;
        }
        //******* EDUARDO END **************//
    }

    /**
     * Informs if there is a accepted value from a replica
     * @param acceptor The replica ID
     * @return True if there is a accepted value from a replica, false otherwise
     */
    public boolean isCommitProofSetted(int acceptor) {
        
        updateArrays();
        
        //******* EDUARDO BEGIN **************//
        int p = this.controller.getCurrentViewPos(acceptor);
        if(p >= 0){
            return commitProof[p] != null;
        }else{
            return false;
        }
        //******* EDUARDO END **************//
    }

    /**
     * Retrives the ACCEPT value from the specified replica
     * @param acceptor The replica ID
     * @return The value from the specified replica
     */
    public byte[] getAccept(int acceptor) {
        
        updateArrays();
        
        //******* EDUARDO BEGIN **************//
        int p = this.controller.getCurrentViewPos(acceptor);
        if(p >= 0){        
            return this.accept[p];
        }else{
            return null;
        }
        //******* EDUARDO END **************//
    }

    /**
     * Retrieves all ACCEPT value from all replicas
     * @return The values from all replicas
     */
    public byte[][] getAccept() {
        return this.accept;
    }

    /**
     * Sets the ACCEPT value from the specified replica
     * @param acceptor The replica ID
     * @param value The valuefrom the specified replica
     */
    public void setAccept(int acceptor, byte[] value) { // TODO: Race condition?
        
        updateArrays();
        
        //******* EDUARDO BEGIN **************//
        int p = this.controller.getCurrentViewPos(acceptor);
        if (p >=0 /*&& !acceptSetted[p] && !isFrozen() */) { //it can only be setted once
            accept[p] = value;
            acceptSetted[p] = true;
        }
        //******* EDUARDO END **************//
    }

    /**
     * Retrieves the accepted value from the specified replica
     * @param acceptor The replica ID
     * @return The value accepted from the specified replica
     */
    public byte[] getCommitProof(int acceptor) {
        
        updateArrays();
        
        //******* EDUARDO BEGIN **************//
         int p = this.controller.getCurrentViewPos(acceptor);
        if(p >= 0){        
        return commitProof[p];
        }else{
            return null;
        }
        //******* EDUARDO END **************//
    }

    /**
     * Retrieves all accepted values from all replicas
     * @return The values accepted from all replicas
     */
    public byte[][] getCommitProof() {
        return commitProof;
    }

    /**
     * Sets the accepted value from the specified replica
     * @param acceptor The replica ID
     * @param value The value accepted from the specified replica
     */
    public void setCommitProof(int acceptor, byte[] value) { // TODO: race condition?
        
        updateArrays();
        
        //******* EDUARDO BEGIN **************//
        int p = this.controller.getCurrentViewPos(acceptor);
        if (p >= 0 /*&& !strongSetted[p] && !isFrozen()*/) { //it can only be setted once
            commitProof[p] = value;
            commitProofSetted[p] = true;
        }
        //******* EDUARDO END **************//
    }

    /**
     * Retrieves the amount of replicas from which this process received a ACCEPT value
     * @param value The value in question
     * @return Amount of replicas from which this process received the specified value
     */
    public int countAccept(byte[] value) {
        return count(acceptSetted, accept, value);
    }

    /**
     * Retrieves the amount of replicas from which this process accepted a specified value
     * @param value The value in question
     * @return Amount of replicas from which this process accepted the specified value
     */
    public int countCommitProof(byte[] value) {
        return count(commitProofSetted, commitProof, value);
    }

    /**
     * Counts how many times 'value' occurs in 'array'
     * @param array Array where to count
     * @param value Value to count
     * @return Ammount of times that 'value' was find in 'array'
     */
    private int count(boolean[] arraySetted,byte[][] array, byte[] value) {
        if (value != null) {
            int counter = 0;
            for (int i = 0; i < array.length; i++) {
                if (arraySetted != null && arraySetted[i] && Arrays.equals(value, array[i])) {
                    counter++;
                }
            }
            return counter;
        }
        return 0;
    }

    /*************************** DEBUG METHODS *******************************/
    /**
     * Print epoch information.
     */
    @Override
    public String toString() {
        StringBuffer buffWrite = new StringBuffer(1024);
        StringBuffer buffAccept = new StringBuffer(1024);

        buffWrite.append("\n\t\tWrites=(");
        buffAccept.append("\n\t\tAccepts=(");

        for (int i = 0; i < accept.length - 1; i++) {
            buffWrite.append("[" + str(accept[i]) + "], ");
            buffAccept.append("[" + str(commitProof[i]) + "], ");
        }

        buffWrite.append("[" + str(accept[accept.length - 1]) +"])");
        buffAccept.append("[" + str(commitProof[commitProof.length - 1]) + "])");

        return "\n\t\tCID=" + consensus.getId() + " \n\t\tTS=" + getTimestamp() + " " + "\n\t\tPropose=[" + (propValueHash != null ? str(propValueHash) : null) + "] " + buffWrite + " " + buffAccept;
    }

    private String str(byte[] obj) {
        if(obj == null) {
            return "null";
        } else {
            return Base64.encodeBase64String(obj);
        }
    }

    @Override
    public boolean equals(Object o) {
        return this == o;
    }
    
    /**
     * Clear all epoch info.
     */
    public void clear() {

        int n = controller.getCurrentViewN();
        
        acceptSetted = new boolean[n];
        commitProofSetted = new boolean[n];

        Arrays.fill(acceptSetted, false);
        Arrays.fill(commitProofSetted, false);

        this.accept = new byte[n][];
        this.commitProof = new byte[n][];

        Arrays.fill((Object[]) accept, null);
        Arrays.fill((Object[]) commitProof, null);
        
        this.proof = new HashSet<ConsensusMessage>();
    }
}
