-- Create table essay_topics and insert initial data

create table essay_topics(
    id serial not null,
    topic_id integer not null,
    title varchar(255) not null,
    created_at timestamptz not null default now(),

    constraint essay_topics_pkey primary key (id),
    constraint essay_topics_topic_id_title_key unique (topic_id, title),
    constraint essay_topics_topic_fkey foreign key (topic_id) references topics (id) on delete cascade
);

insert into essay_topics (topic_id, title)
select t.id, v.title
from topics t
join (values
    ('Terveys ja hyvinvointi', 'Tarvitset apteekista jotain ja pyydät, että ystävä auttaa.'),
    ('Terveys ja hyvinvointi', 'Olet nähnyt auto-onnettomuuden. Poliisi kysyy sinulta, mitä tapahtui. Vastaa poliisille.'),
    ('Terveys ja hyvinvointi', 'Olet lenkillä ja näet, että nuori nainen makaa kadulla. Mitä sanot hänelle?'),
    ('Ihminen ja lähipiiri', 'Sinun naapurin oven takana käy joku, mutta naapuri ei ole kotona. Kerro naapurille, miltä vieras näytti.'),
    ('Ihminen ja lähipiiri', 'Näet kadulla, että kaksi poikaa kiusaa koulukaveria. Mene väliin ja kerro pojille, miksi ei saa kiusata.'),
    ('Ihminen ja lähipiiri', 'Olet ystävän luona kylässä. Ystävä tarjoaa sinulle ruokaa tai juomaa, jota et voi syödä / juoda (keksi itse, miksi). Kieltäydy kohteliaasti.'),
    ('Arkielämä', 'Menet kahvilaan, mutta kaikki pöydät ovat likaisia. Valita asiasta tarjoilijalle.'),
    ('Arkielämä', 'Kaveri on lainannut sinulta autoa. Hän on käyttänyt kaikki bensat ja ei ole tankannut. Kerro kaverille, mitä mieltä olet hänen käytöksestä.'),
    ('Arkielämä', 'Haluat palkata siivoojan kotiin. Kerro ystävällesi, miksi et enää halua siivota itse.'),
    ('Luonto ja ympäristö', 'Löydät sohvan, jääkaapin ja paljon roskapusseja metsästä. Soitat kaatopaikalle ja kerrot tilanteesta.'),
    ('Luonto ja ympäristö', 'Olet järven rannalla ja siellä on sorsia. Mummo syöttää sorsille suklaata. Mitä sanot mummolle?'),
    ('Luonto ja ympäristö', 'Olet kaupassa ja haluat ostaa hyvät kengät metsään kävelyä varten. Pyydät apua myyjältä.'),
    ('Työ ja koulutus', 'Sinun työpaikalle on tullut uusi työntekijä. Hän ei tiedä, mitä tehdä. Auta häntä.'),
    ('Työ ja koulutus', 'Olet ollut työhaastattelussa. Soitat haastattelijalle, koska haluat vielä kysyä jotakin. (Keksi itse, mitä.)'),
    ('Työ ja koulutus', 'Sinun ystävä on saanut potkut. Mitä sanot?'),
    ('Vapaa-aika ja harrastukset', 'Haluat palauttaa kirjan kirjastoon, mutta se on mennyt rikki. Selitä, mitä tapahtui.'),
    ('Vapaa-aika ja harrastukset', 'Luet kirjaa kirjastossa, kun joku puhuu kovaan ääneen puhelimessa. Huomauta hänelle asiasta.'),
    ('Vapaa-aika ja harrastukset', 'Portsari ei päästä sinua sisälle ja sanoo, että näytät alaikäiseltä. Mitä sanot?'),
    ('Yhteiskunta', 'Sinulla on jokin ongelma oleskeluluvan kanssa (keksi itse, mikä). Soita Migriin.'),
    ('Yhteiskunta', 'Olet saanut YKI-testin tulokset. Haluat hakea kansalaisuutta, mutta sinulla on kysymyksiä. Soita Migriin.'),
    ('Yhteiskunta', 'Sinun kännykkä on mennyt rikki (keksi itse, miten). Kysyt, korvaako kotivakuutus sen.')
) as v(topic_name, title) on t.name = v.topic_name;