--
-- PostgreSQL database dump
--

-- Dumped from database version 14.1
-- Dumped by pg_dump version 14.1

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: certifiable_item; Type: TYPE; Schema: public; Owner: postgres
--

CREATE TYPE public.certifiable_item AS ENUM (
    'course',
    'workshop'
);


ALTER TYPE public.certifiable_item OWNER TO postgres;

--
-- Name: certifiable_type; Type: TYPE; Schema: public; Owner: postgres
--

CREATE TYPE public.certifiable_type AS ENUM (
    'course',
    'workshop'
);


ALTER TYPE public.certifiable_type OWNER TO postgres;

SET default_tablespace = '';


--
-- Name: author; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.author (
    id integer NOT NULL,
    first_name text NOT NULL,
    last_name text NOT NULL
);


ALTER TABLE public.author OWNER TO postgres;

--
-- Name: author_course; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.author_course (
    author_id integer NOT NULL,
    course_id integer NOT NULL
);


ALTER TABLE public.author_course OWNER TO postgres;

--
-- Name: author_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.author_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.author_id_seq OWNER TO postgres;

--
-- Name: author_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.author_id_seq OWNED BY public.author.id;


--
-- Name: certificate; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.certificate (
    id integer NOT NULL,
    item_type public.certifiable_item NOT NULL,
    item_id integer NOT NULL,
    customer_id uuid NOT NULL
);


ALTER TABLE public.certificate OWNER TO postgres;

--
-- Name: certificate_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.certificate_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.certificate_id_seq OWNER TO postgres;

--
-- Name: certificate_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.certificate_id_seq OWNED BY public.certificate.id;


--
-- Name: continent; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.continent (
    id integer NOT NULL,
    name text NOT NULL
);


ALTER TABLE public.continent OWNER TO postgres;

--
-- Name: continent_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.continent_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.continent_id_seq OWNER TO postgres;

--
-- Name: continent_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.continent_id_seq OWNED BY public.continent.id;


--
-- Name: country; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.country (
    id integer NOT NULL,
    name text NOT NULL,
    continent_identifier integer
);


ALTER TABLE public.country OWNER TO postgres;

--
-- Name: country_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.country_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.country_id_seq OWNER TO postgres;

--
-- Name: country_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.country_id_seq OWNED BY public.country.id;


--
-- Name: course; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.course (
    id integer NOT NULL,
    title text NOT NULL,
    rating integer NOT NULL
);


ALTER TABLE public.course OWNER TO postgres;

--
-- Name: course_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.course_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.course_id_seq OWNER TO postgres;

--
-- Name: course_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.course_id_seq OWNED BY public.course.id;


--
-- Name: customer; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.customer (
    id uuid NOT NULL,
    first_name text NOT NULL,
    last_name text NOT NULL
);


ALTER TABLE public.customer OWNER TO postgres;

--
-- Name: employee; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.employee (
    id integer NOT NULL,
    first_name character varying(20) NOT NULL,
    last_name character varying(20) NOT NULL,
    employee_reports_to_id integer
);


ALTER TABLE public.employee OWNER TO postgres;

--
-- Name: employee_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.employee_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.employee_id_seq OWNER TO postgres;

--
-- Name: employee_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.employee_id_seq OWNED BY public.employee.id;


--
-- Name: population; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.population (
    id integer NOT NULL,
    value integer
);


ALTER TABLE public.population OWNER TO postgres;

--
-- Name: scalar; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.scalar (
    id integer NOT NULL,
    timestamp_c timestamp without time zone,
    timestamp_with_fraction timestamp without time zone,
    timestamp_without_timezone_c timestamp without time zone,
    timestamptz_c timestamp with time zone,
    timestamptz_with_fraction timestamp with time zone,
    timestamp_with_timezone_c timestamp with time zone,
    money_c money,
    decimal_c numeric(8,4),
    numeric_c numeric(12,6),
    bigint_c bigint,
    int8_c bigint,
    bigserial_c bigint NOT NULL,
    serial8_c bigint NOT NULL,
    date_c date,
    time_c time without time zone,
    time_with_time_zone time with time zone
);


ALTER TABLE public.scalar OWNER TO postgres;

--
-- Name: scalar_bigserial_c_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.scalar_bigserial_c_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.scalar_bigserial_c_seq OWNER TO postgres;

--
-- Name: scalar_bigserial_c_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.scalar_bigserial_c_seq OWNED BY public.scalar.bigserial_c;


--
-- Name: scalar_serial8_c_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.scalar_serial8_c_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.scalar_serial8_c_seq OWNER TO postgres;

--
-- Name: scalar_serial8_c_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.scalar_serial8_c_seq OWNED BY public.scalar.serial8_c;


--
-- Name: site; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.site (
    id integer NOT NULL,
    name text NOT NULL
);


ALTER TABLE public.site OWNER TO postgres;

--
-- Name: site_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.site_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.site_id_seq OWNER TO postgres;

--
-- Name: site_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.site_id_seq OWNED BY public.site.id;


--
-- Name: site_meta_datum; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.site_meta_datum (
    id integer NOT NULL,
    description text NOT NULL
);


ALTER TABLE public.site_meta_datum OWNER TO postgres;

--
-- Name: workshop; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.workshop (
    id integer NOT NULL,
    name text NOT NULL
);


ALTER TABLE public.workshop OWNER TO postgres;

--
-- Name: workshop_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.workshop_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.workshop_id_seq OWNER TO postgres;

--
-- Name: workshop_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.workshop_id_seq OWNED BY public.workshop.id;


--
-- Name: author id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.author ALTER COLUMN id SET DEFAULT nextval('public.author_id_seq'::regclass);


--
-- Name: certificate id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.certificate ALTER COLUMN id SET DEFAULT nextval('public.certificate_id_seq'::regclass);


--
-- Name: continent id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.continent ALTER COLUMN id SET DEFAULT nextval('public.continent_id_seq'::regclass);


--
-- Name: country id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.country ALTER COLUMN id SET DEFAULT nextval('public.country_id_seq'::regclass);


--
-- Name: course id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.course ALTER COLUMN id SET DEFAULT nextval('public.course_id_seq'::regclass);


--
-- Name: employee id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.employee ALTER COLUMN id SET DEFAULT nextval('public.employee_id_seq'::regclass);


--
-- Name: scalar bigserial_c; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.scalar ALTER COLUMN bigserial_c SET DEFAULT nextval('public.scalar_bigserial_c_seq'::regclass);


--
-- Name: scalar serial8_c; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.scalar ALTER COLUMN serial8_c SET DEFAULT nextval('public.scalar_serial8_c_seq'::regclass);


--
-- Name: site id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.site ALTER COLUMN id SET DEFAULT nextval('public.site_id_seq'::regclass);


--
-- Name: workshop id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.workshop ALTER COLUMN id SET DEFAULT nextval('public.workshop_id_seq'::regclass);


--
-- Data for Name: author; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.author (id, first_name, last_name) FROM stdin;
1	John	Doe
2	Rahul	Sharma
3	Prakash	Rao
\.


--
-- Data for Name: author_course; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.author_course (author_id, course_id) FROM stdin;
1	1
1	2
2	3
2	4
\.


--
-- Data for Name: certificate; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.certificate (id, item_type, item_id, customer_id) FROM stdin;
1	course	4	847f09a7-39d1-4021-b43d-18ceb7ada8f6
2	workshop	1	e5156dce-58ff-44f5-8533-932a7250bd29
\.


--
-- Data for Name: continent; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.continent (id, name) FROM stdin;
1	Asia
\.


--
-- Data for Name: country; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.country (id, name, continent_identifier) FROM stdin;
1	India	1
\.


--
-- Data for Name: course; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.course (id, title, rating) FROM stdin;
1	Flutter Fundamentals	4
2	Beginning React Native	5
3	Android Basics	5
4	Getting Started With Kotlin	5
\.


--
-- Data for Name: customer; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.customer (id, first_name, last_name) FROM stdin;
847f09a7-39d1-4021-b43d-18ceb7ada8f6	John	Doe
e5156dce-58ff-44f5-8533-932a7250bd29	Rahul	Sharma
\.


--
-- Data for Name: employee; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.employee (id, first_name, last_name, employee_reports_to_id) FROM stdin;
1	Andrew	Adams	\N
2	Nancy	Edwards	1
3	Jane	Peacock	2
4	Margaret	Park	2
5	Steve	Johnson	2
6	Michael	Mitchell	1
7	Robert	King	6
8	Laura	Callahan	6
\.


--
-- Data for Name: population; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.population (id, value) FROM stdin;
1	212313112
\.


--
-- Data for Name: scalar; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.scalar (id, timestamp_c, timestamp_with_fraction, timestamp_without_timezone_c, timestamptz_c, timestamptz_with_fraction, timestamp_with_timezone_c, money_c, decimal_c, numeric_c, bigint_c, int8_c, bigserial_c, serial8_c, date_c, time_c, time_with_time_zone) FROM stdin;
1	2004-10-19 10:23:54	2004-10-19 10:23:54.222	2004-10-19 10:23:54	2017-08-19 17:52:11+05:30	2017-08-19 17:52:11.222+05:30	2017-08-19 17:52:11+05:30	$5,234.57	5234.5678	5234.678908	9223372036854775807	8223372036854775807	7223372036854775807	6223372036854775807	1988-11-05	13:05:54	13:05:54+05:30
\.


--
-- Data for Name: site; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.site (id, name) FROM stdin;
1	test
\.


--
-- Data for Name: site_meta_datum; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.site_meta_datum (id, description) FROM stdin;
1	test dec
\.


--
-- Data for Name: workshop; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.workshop (id, name) FROM stdin;
1	Introduction to Kafka
2	Introduction to Docker
\.


--
-- Name: author_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.author_id_seq', 3, true);


--
-- Name: certificate_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.certificate_id_seq', 2, true);


--
-- Name: continent_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.continent_id_seq', 1, false);


--
-- Name: country_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.country_id_seq', 1, false);


--
-- Name: course_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.course_id_seq', 4, true);


--
-- Name: employee_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.employee_id_seq', 8, true);


--
-- Name: scalar_bigserial_c_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.scalar_bigserial_c_seq', 1, false);


--
-- Name: scalar_serial8_c_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.scalar_serial8_c_seq', 1, false);


--
-- Name: site_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.site_id_seq', 1, false);


--
-- Name: workshop_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.workshop_id_seq', 2, true);


--
-- Name: author_course author_course_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.author_course
    ADD CONSTRAINT author_course_pkey PRIMARY KEY (author_id, course_id);


--
-- Name: author author_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.author
    ADD CONSTRAINT author_pkey PRIMARY KEY (id);


--
-- Name: certificate certificate_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.certificate
    ADD CONSTRAINT certificate_pkey PRIMARY KEY (id);


--
-- Name: continent continent_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.continent
    ADD CONSTRAINT continent_pkey PRIMARY KEY (id);


--
-- Name: country country_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.country
    ADD CONSTRAINT country_pkey PRIMARY KEY (id);


--
-- Name: course course_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.course
    ADD CONSTRAINT course_pkey PRIMARY KEY (id);


--
-- Name: customer customer_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.customer
    ADD CONSTRAINT customer_pkey PRIMARY KEY (id);


--
-- Name: employee employee_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.employee
    ADD CONSTRAINT employee_pkey PRIMARY KEY (id);


--
-- Name: population population_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.population
    ADD CONSTRAINT population_pkey PRIMARY KEY (id);


--
-- Name: scalar scalar_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.scalar
    ADD CONSTRAINT scalar_pkey PRIMARY KEY (id);


--
-- Name: site_meta_datum site_meta_datum_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.site_meta_datum
    ADD CONSTRAINT site_meta_datum_pkey PRIMARY KEY (id);


--
-- Name: site site_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.site
    ADD CONSTRAINT site_pkey PRIMARY KEY (id);


--
-- Name: workshop workshop_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.workshop
    ADD CONSTRAINT workshop_pkey PRIMARY KEY (id);


--
-- Name: author_course author_course_author_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.author_course
    ADD CONSTRAINT author_course_author_id_fkey FOREIGN KEY (author_id) REFERENCES public.author(id);


--
-- Name: author_course author_course_course_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.author_course
    ADD CONSTRAINT author_course_course_id_fkey FOREIGN KEY (course_id) REFERENCES public.course(id);


--
-- Name: certificate certificate_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.certificate
    ADD CONSTRAINT certificate_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customer(id);


--
-- Name: country country_continent_identifier_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.country
    ADD CONSTRAINT country_continent_identifier_fkey FOREIGN KEY (continent_identifier) REFERENCES public.continent(id);


--
-- Name: employee employee_reports_to_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.employee
    ADD CONSTRAINT employee_reports_to_fkey FOREIGN KEY (employee_reports_to_id) REFERENCES public.employee(id);


--
-- Name: population population_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.population
    ADD CONSTRAINT population_id_fkey FOREIGN KEY (id) REFERENCES public.continent(id);


--
-- Name: site_meta_datum site_meta_datum_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.site_meta_datum
    ADD CONSTRAINT site_meta_datum_id_fkey FOREIGN KEY (id) REFERENCES public.site(id);


--
-- PostgreSQL database dump complete
--

CREATE TABLE public.account (
   acc_num INTEGER,
   acc_type INTEGER,
   acc_descr VARCHAR(20),
   PRIMARY KEY (acc_num, acc_type));

COPY public.account (acc_num, acc_type, acc_descr) FROM stdin;
1	1	SA #1
2	1	SA #2
\.

CREATE TABLE public.account_referrer (
   acc_num INTEGER,
   acc_type INTEGER,
   referred_by VARCHAR(20),
   PRIMARY KEY (acc_num, acc_type),
   FOREIGN KEY (acc_num, acc_type) REFERENCES public.account
);

INSERT INTO "public"."account_referrer"("acc_num","acc_type","referred_by")
VALUES
(1,1,E'foo');

CREATE TABLE public.sub_account (
   sub_acc INTEGER PRIMARY KEY,
   ref_num INTEGER NOT NULL,
   ref_type INTEGER NOT NULL,
   sub_descr VARCHAR(20),
   FOREIGN KEY (ref_num, ref_type) REFERENCES public.account);

INSERT INTO "public"."sub_account"("sub_acc","ref_num","ref_type","sub_descr")
VALUES
(1,1,1,E'SA #1 Sub Acc'),
(3,1,1,E'SA #1 Sub Acc#2'),
(2,2,1,E'SA #2 Sub Acc');