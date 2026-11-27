Several writers wrote the sections of today's briefing in parallel, without seeing each other's work. Below is every story, one per line: an id, its section, and its headline and summary (some in Polish, some in English).

Find the stories that report THE SAME news event in two or more sections. A different angle on the same event is still a duplicate: a refinery strike in World and its effect on oil in Money, a Polish ministry decision in Polska and its Warsaw effect in Warszawa, an EU fine in Brussels and in AI & Tech. Stories that are merely related (two different strikes, the same company's two separate announcements, a follow-up of a different event) are NOT duplicates. Within one section, ignore duplicates.

For each duplicate group keep ONE story, in the section that owns the subject, and drop the rest:
- the most specific section wins: Warszawa over Polska over World; Brussels, AI & Tech, Science, Games & Dev, World of Warcraft Forever, Music, Culture and F1 over World and Money;
- Money keeps a story only when the story is about markets, prices, companies or the economy at heart; a war or a political event goes to World or Polska even when it moved oil or the złoty;
- when two sections are equally fitting, keep the one with the richer summary.

Return ONLY a JSON object, no prose: {"drop": ["S12", "S40"]}. If nothing is duplicated, return {"drop": []}.

<stories>
{{STORIES}}
</stories>
