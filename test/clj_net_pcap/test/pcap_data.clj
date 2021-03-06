;;;
;;; Copyright (C) 2013 Ruediger Gad
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
    :doc "Clojure tests with respect to handling data."}
  clj-net-pcap.test.pcap-data
  (:use clojure.test
        clj-net-pcap.core
        clj-net-pcap.native
        clj-net-pcap.pcap-data
        clj-assorted-utils.util)
  (:import (clj_net_pcap PcapByteArrayTimeStampComparator)))

(def test-file "test/clj_net_pcap/test/data/offline-test.pcap")

(deftest test-guess-class-c-net
  (let [ip-addr "192.168.42.123"]
    (is (= :class-c (network-class ip-addr)))
    (is (= "192.168.42.0" (guess-subnet ip-addr)))
    (is (= "255.255.255.0" (guess-subnet-mask ip-addr)))
    (is (= 24 (guess-subnet-mask-bits ip-addr)))))

(deftest test-guess-class-a-net
  (let [ip-addr "10.123.45.67"]
    (is (= :class-a (network-class ip-addr)))
    (is (= "10.0.0.0" (guess-subnet ip-addr)))
    (is (= "255.0.0.0" (guess-subnet-mask ip-addr)))
    (is (= 8 (guess-subnet-mask-bits ip-addr)))))

(deftest test-guess-class-b-net
  (let [ip-addr "172.16.123.45"]
    (is (= :class-b (network-class ip-addr)))
    (is (= "172.16.0.0" (guess-subnet ip-addr)))
    (is (= "255.255.0.0" (guess-subnet-mask ip-addr)))
    (is (= 16 (guess-subnet-mask-bits ip-addr)))))

(deftest test-guess-class-b-net-2
  (let [ip-addr "172.31.123.45"]
    (is (= :class-b (network-class ip-addr)))
    (is (= "172.31.0.0" (guess-subnet ip-addr)))
    (is (= "255.255.0.0" (guess-subnet-mask ip-addr)))
    (is (= 16 (guess-subnet-mask-bits ip-addr)))))

(deftest test-timestamp-equals
  (let [my-raw-data (extract-byte-arrays-from-pcap-file test-file)
        my-comparator (PcapByteArrayTimeStampComparator.)]
    (is (.equals my-comparator (my-raw-data 0) (my-raw-data 0)))
    (is (not (.equals my-comparator (my-raw-data 0) (my-raw-data 1))))))

(deftest test-timestamp-compare-to-1
  (let [my-raw-data (extract-byte-arrays-from-pcap-file test-file)
        my-comparator (PcapByteArrayTimeStampComparator.)]
    (is (= -1 (.compare my-comparator (my-raw-data 0) (my-raw-data 1))))))

(deftest test-timestamp-compare-to-2
  (let [my-raw-data (extract-byte-arrays-from-pcap-file test-file)
        my-comparator (PcapByteArrayTimeStampComparator.)]
    (is (= 1 (.compare my-comparator (my-raw-data 1) (my-raw-data 0))))))
