;;;
;;; Copyright (C) 2015 Ruediger Gad
;;;
;;; This file is part of clj-net-pcap.
;;; clj-net-pcap is licensed under the terms of either
;;; - the GNU Lesser General Public License (LGPL) version 3 or later
;;;   http://www.gnu.org/licenses/lgpl-3.0.html
;;; or, at your option,
;;; - the Eclipse Public License (EPL) 1.0.
;;;   http://opensource.org/licenses/eclipse-1.0.php
;;;
;;; By contributing to clj-net-pcap, you agree that your contribution will be licensed under both licenses
;;; and that users of clj-net-pcap can chose any of these licenses.
;;;

(ns 
  ^{:author "Ruediger Gad",
    :doc "Tests for the bulk capture mode."}
  clj-net-pcap.test.pcap-bulk
  (:use clojure.test
        clj-assorted-utils.util
        clj-net-pcap.core
        clj-net-pcap.packet-gen
        clj-net-pcap.pcap
        clj-net-pcap.pcap-data
        clj-net-pcap.sniffer)
  (:import (java.util Arrays)
           (java.nio ByteBuffer)
           (org.jnetpcap DirectBulkByteBufferWrapper)))

(def test-pkt-bytes [-1 -2 -3 -14 -15 -16 1 2 3 4 5 6 8 0
                     69 0 0 40 0 3 64 0 7 1 115 -49 1 2 3 4 -4 -3 -2 -1
                     8 0 50 -78 0 123 0 12 0 0 0 0 0 0 0 0 97 98 99 100])

(def test-pkt-descr-map {"len" 54, "ethSrc" "01:02:03:04:05:06", "ethDst" "FF:FE:FD:F2:F1:F0",
                         "ipVer" 4, "ipDst" "252.253.254.255", "ipId" 3, "ipType" 1,
                         "ipTtl" 7, "ipSrc" "1.2.3.4",
                         "icmpType" 8, "icmpEchoSeq" "bar",
                         "icmpId" 123, "icmpSeqNo" 12, "data" "abcd"})



(deftest cljnetpcap-send-and-receive-bytes-packet-via-intermediate-buffer-count-test
  (let [ba (byte-array (map byte test-pkt-bytes))
        cntr (counter)
        received-data (ref nil)
        forwarder-fn (fn [data]
                       (dosync (ref-set received-data data))
                       (cntr inc))
        cljnetpcap (binding [clj-net-pcap.core/*bulk-size* 10
                             clj-net-pcap.core/*emit-raw-data* true
                             clj-net-pcap.core/*use-intermediate-buffer* true]
                     (create-and-start-online-cljnetpcap forwarder-fn lo))
        _ (add-filter cljnetpcap "icmp and (dst host 252.253.254.255) and (src host 1.2.3.4)")]
    (sleep 1000)
    (cljnetpcap :send-bytes-packet ba 10 10)
    (sleep 1000)
    (is (= 1 (cntr)))
    (is (not (.isDirect @received-data)))
    (is (.hasArray @received-data))
    (stop-cljnetpcap cljnetpcap)))

(deftest cljnetpcap-send-and-receive-packet-maps-via-intermediate-buffer-count-test
  (let [cntr (counter)
        data-inst-len (+ 16 (count test-pkt-bytes))
        bb (ByteBuffer/allocate (* data-inst-len 10))
        forwarder-fn (fn [data]
                       (doto bb
                         (.put data)
                         (.flip))
                       (cntr inc))
        cljnetpcap (binding [clj-net-pcap.core/*bulk-size* 10
                             clj-net-pcap.core/*emit-raw-data* true
                             clj-net-pcap.core/*use-intermediate-buffer* true]
                     (create-and-start-online-cljnetpcap forwarder-fn lo))
        _ (add-filter cljnetpcap "icmp and (dst host 252.253.254.255) and (src host 1.2.3.4)")]
    (sleep 1000)
    (doseq [x (range 0 10)]
      (cljnetpcap :send-packet-map (assoc test-pkt-descr-map "icmpSeqNo" x)))
    (sleep 1000)
    (is (= 1 (cntr)))
    (doseq [x (range 0 10)]
      (is (= 123 (.get bb (+ (+ 40 15) (* x data-inst-len)))))
      (is (= x (.get bb (+ (+ 42 15) (* x data-inst-len))))))
    (stop-cljnetpcap cljnetpcap)))

(deftest cljnetpcap-send-and-receive-bytes-packet-without-intermediate-buffer-count-test
  (let [ba (byte-array (map byte test-pkt-bytes))
        cntr (counter)
        received-data (ref nil)
        forwarder-fn (fn [data]
                       (dosync (ref-set received-data data))
                       (cntr inc)
                       (.freeNativeMemory data))
        cljnetpcap (binding [clj-net-pcap.core/*bulk-size* 10
                             clj-net-pcap.core/*emit-raw-data* true
                             clj-net-pcap.core/*use-intermediate-buffer* false]
                     (create-and-start-online-cljnetpcap forwarder-fn lo))
        _ (add-filter cljnetpcap "icmp and (dst host 252.253.254.255) and (src host 1.2.3.4)")]
    (sleep 1000)
    (cljnetpcap :send-bytes-packet ba 10 10)
    (sleep 1000)
    (is (= 1 (cntr)))
    (is (= DirectBulkByteBufferWrapper (type @received-data)))
    (stop-cljnetpcap cljnetpcap)))

(deftest cljnetpcap-send-and-receive-packet-maps-without-intermediate-buffer-count-test
  (let [cntr (counter)
        data-inst-len (+ 16 (count test-pkt-bytes))
        received-data (ref nil)
        forwarder-fn (fn [data]
                       (dosync (ref-set received-data data))
                       (cntr inc))
        cljnetpcap (binding [clj-net-pcap.core/*bulk-size* 10
                             clj-net-pcap.core/*emit-raw-data* true
                             clj-net-pcap.core/*use-intermediate-buffer* false]
                     (create-and-start-online-cljnetpcap forwarder-fn lo))
        _ (add-filter cljnetpcap "icmp and (dst host 252.253.254.255) and (src host 1.2.3.4)")]
    (sleep 1000)
    (doseq [x (range 0 10)]
      (cljnetpcap :send-packet-map (assoc test-pkt-descr-map "icmpSeqNo" x)))
    (sleep 1000)
    (is (= 1 (cntr)))
    (is (.isDirect (.getBuffer @received-data)))
    (is (not (.hasArray (.getBuffer @received-data))))
    (doseq [x (range 0 10)]
      (is (= 123 (.get (.getBuffer @received-data) (+ (+ 40 15) (* x data-inst-len)))))
      (is (= x (.get (.getBuffer @received-data) (+ (+ 42 15) (* x data-inst-len))))))
    (.freeNativeMemory @received-data)
    (stop-cljnetpcap cljnetpcap)))

