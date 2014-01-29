;;;
;;; Copyright (C) 2012 Ruediger Gad
;;;
;;; This file is part of clj-net-pcap.
;;;
;;; clj-net-pcap is free software: you can redistribute it and/or modify
;;; it under the terms of the GNU Lesser General Public License (LGPL) as
;;; published by the Free Software Foundation, either version 3 of the License,
;;; or (at your option any later version.
;;;
;;; clj-net-pcap is distributed in the hope that it will be useful,
;;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;;; GNU Lesser General Public License (LGPL) for more details.
;;;
;;; You should have received a copy of the GNU Lesser General Public License (LGPL)
;;; along with clj-net-pcap.  If not, see <http://www.gnu.org/licenses/>.
;;;

(ns
  ^{:author "Ruediger Gad",
    :doc "Main class and method for launching a simple clj-net-pcap based sniffer
          that prints some information about the captured packets to stdout.
          This is primarily intended for testing and documentation purposes."}
  clj-net-pcap.main
  (:use clojure.pprint
        [clojure.string :only [join split]]
        clojure.tools.cli
        clj-net-pcap.core
        clj-net-pcap.native
        clj-net-pcap.pcap-data
        clj-assorted-utils.util)
  (:gen-class))

(defn -main [& args]
  (let [cli-args (cli args
                      ["-i" "--interface"
                       "Interface on which the packets are captured"
                       :default "eth0"]
                      ["-f" "--filter"
                       (str "Pcap filter to be used. "
                            "Defaults to the empty String which means that all "
                            "packets are captured.")
                       :default ""]
                      ["-s" "--stats"
                       (str "Print stats to stderr in a regular interval."
                            "The interval is given as parameter in milliseconds."
                            "Values smaller equal 0 mean that no stats are printed."
                            "Defaults to 0.")
                       :default 0
                       :parse-fn #(Integer. %)]
                      ["-S" "--snap-len"
                       (str "The snaplen to use."
                            "This determines how many bytes of data will be captured from each packet.")
                       :default 128
                       :parse-fn #(Integer. %)]
                      ["-B" "--buffer-size"
                       "The buffer size to use."
                       :default (int (Math/pow 2 26))
                       :parse-fn #(Integer. %)]
                      ["-F" "--forwarder-fn"
                       (str "Use the specified function as forwarder function for "
                            "processing packets.\n"
                            "Available function names are:\n"
                            "stdout-combined-forwarder-fn, stdout-byte-array-forwarder-fn, "
                            "stdout-forwarder-fn, no-op-converter-forwarder-fn, "
                            "counting-converter-forwarder-fn, calls-per-second-converter-forwarder-fn")
                       :default "stdout-forwarder-fn"]
                      ["-T" "--transformation-fn"
                       (str "Use the specified function for transforming the raw packets."
                            "Available function names are:\n"
                            "pcap-packet-to-bean, pcap-packet-to-map, "
                            "pcap-packet-to-nested-maps, pcap-packet-to-no-op")
                       :default "pcap-packet-to-bean"]
                      ["-d" "--duration"
                       "The duration in seconds how long clj-net-pcap is run."
                       :default -1
                       :parse-fn #(Integer. %)]
                      ["-r" "--raw"
                       (str "Emit raw data instead of decoded packets. "
                            "Be careful, not all transformation and forwarder functions support this.")
                       :flag true]
                      ["-h" "--help" "Print this help." :flag true])
        arg-map (cli-args 0)
        help-string (cli-args 2)]
    (if (arg-map :help)
      (println help-string)
      (do
        (println "Starting clj-net-pcap using the following options:")
        (pprint arg-map)
        (let [cljnetpcap (binding [clj-net-pcap.pcap/*snap-len* (:snap-len arg-map)
                                   clj-net-pcap.pcap/*buffer-size* (:buffer-size arg-map)]
                           (create-and-start-online-cljnetpcap
                             (let [f-tmp (resolve (symbol (str "clj-net-pcap.pcap-data/" (arg-map :forwarder-fn))))
                                   f (if (= 'packet (first (first (:arglists (meta f-tmp)))))
                                       f-tmp
                                       (f-tmp))
                                   t (resolve (symbol (str "clj-net-pcap.pcap-data/" (arg-map :transformation-fn))))]
                                 #(let [o (t %)]
                                    (if o
                                      (f o))))
                             (arg-map :interface)
                             (arg-map :filter)
                             (arg-map :raw)))
              stat-interval (arg-map :stats)
              stat-out-executor (executor)
              shutdown-fn (fn [] (do
                                   (println "clj-net-pcap is shuting down...")
                                   (when (> stat-interval 0)
                                     (println "Stopping stat output.")
                                     (shutdown stat-out-executor))
                                   (print-stat-cljnetpcap cljnetpcap)
                                   (stop-cljnetpcap cljnetpcap)
                                   (println "Removing temporarily extracted native libs...")
                                   (remove-native-libs)))
              run-duration (arg-map :duration)
              shutdown-timer-executor (executor)]
          (println "clj-net-pcap standalone executable started.\n")
          (when (> stat-interval 0)
            (println "Printing stats to stderr in intervalls of" stat-interval "ms.")
            (run-repeat stat-out-executor #(print-stat-cljnetpcap cljnetpcap) stat-interval))
          (if (> run-duration 0)
            (do
              (println "Will automatically shut down in" run-duration "seconds.")
              (run-once shutdown-timer-executor shutdown-fn (* 1000 run-duration)))
          ;;; Running the main from, e.g., leiningen results in stdout not being properly accessible.
          ;;; Hence, this will not work when run this way but works when run from a jar via "java -jar ...".
            (do
              (println "Type \"quit\" or \"q\" to quit: ")
              (loop [line ""]
                (if (or (= line "q") (= line "quit"))
                  nil
                  (let [split-input (split line #"\s")
                        cmd (first split-input)
                        args (join " " (rest split-input))]
                    (cond
                      (or
                        (= cmd "af")
                        (= cmd "add-filter")) (try 
                                                (add-filter cljnetpcap args)
                                                (catch Exception e
                                                  (println "Error adding filter:" e)))
                      (or
                        (= cmd "sf")
                        (= cmd "show-filters")) (pprint (get-filters cljnetpcap))
                      (or
                        (= cmd "rlf")
                        (= cmd "remove-last-filter")) (remove-last-filter cljnetpcap)
                      :default (when (not= cmd "")
                                 (println "Unknown command:" cmd)
                                 (println "Valid commands are: add-filter (af), show-filters (sf), remove-last-filter (rlf)")))
                    (print "cljnetpcap=> ")
                    (flush)
                    (recur (read-line)))))
              (shutdown-fn)))
          (println "Leaving (-main [& args] ...)."))))))

