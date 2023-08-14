(ns suite-reader
  (:import [java.time LocalDateTime LocalDate LocalTime OffsetDateTime OffsetTime]
           [java.time.format DateTimeFormatter DateTimeFormatterBuilder]
           [java.time.temporal ChronoField]))

(def ^:private date-time-formatter
  (-> (DateTimeFormatterBuilder.)
      (.append DateTimeFormatter/ISO_LOCAL_DATE_TIME)
      (.appendFraction ChronoField/MICRO_OF_SECOND 0 6 true)
      .toFormatter))

(def ^:private date-time-with-time-zone-formatter
  (-> (DateTimeFormatterBuilder.)
      (.append DateTimeFormatter/ISO_OFFSET_DATE_TIME)
      (.appendFraction ChronoField/MICRO_OF_SECOND 0 6 true)
      .toFormatter))

(def read-string-opts
  {:readers {'local-date-time  #(LocalDateTime/parse % date-time-formatter)
             'local-date       #(LocalDate/parse %)
             'local-time       #(LocalTime/parse %)
             'offset-date-time #(OffsetDateTime/parse % date-time-with-time-zone-formatter)
             'offset-time      #(OffsetTime/parse %)
             'mysql-timestamp  #(LocalDateTime/parse % (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss.SSSSSS"))}})