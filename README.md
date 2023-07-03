curl https://api.openai.com/v1/chat/completions \
-H "Content-Type: application/json" \
-H "Authorization: Bearer $OPEN_AI_KEY" \
-d '{
"model": "gpt-3.5-turbo", 
"messages": [{"role": "user", "content": "what place can you recommend in Barcelona?"}], 
"temperature": 0.0 
}'

curl https://api.openai.com/v1/chat/completions \
-H "Content-Type: application/json" \
-H "Authorization: Bearer $OPEN_AI_KEY" \
-d '{
"model": "gpt-3.5-turbo",
"messages": [{"role": "user", "content": "Say this is a test!"}],
"temperature": 0.7
}'

curl https://api.openai.com/v1/chat/completions \
-H "Content-Type: application/json" \
-H "Authorization: Bearer $OPEN_AI_KEY" \
-d '{
"model": "gpt-3.5-turbo",
"messages": [{"role": "user", "content": "Your goal is to summarize a text in one sentence of maximum 200 characters. The readers are very smart scientists, knowing the domain very well. The text is delimited with #. # Hepatocellular carcinoma (HCC) is the most frequent primary liver cancer, however, only 20 - 30% benefit from potentially curative treatments, including liver resection or transplantation. The advent of various pharmacological treatments with high objective response rates expanded the horizon of treatment strategy, especially aiming at downstaging to resectable tumor status.In this article, conventional treatments and recent progress in pharmacotherapy for advanced HCC, aiming at downstaging from unresectable to resectable status, are reviewed. Future prospectives of combination therapies using immune checkpoint inhibitors were also introduced by reviewing recent clinical trials, paying attention to the objective response rate as its potential of downstaging treatments. The PubMed database was searched using terms: hepatocellular carcinoma, downstage, conversion, chemotherapy; study types: clinical trials, systematic reviews, and meta-analyses; and time criterion 2002 to present. The newly developed pharmacological therapies, such as lenvatinib, atezolizumab plus bevacizumab, or durvalumab plus tremelimumab, showed a high objective response rate of up to 30%. Although various tumor statuses in advanced HCC hamper detailed analysis of successful conversion rate, the novel combined immunotherapies are expected to provide more opportunities for subsequent curative surgery for initially unresectable advanced HCC. The conversion treatment strategies for unresectable HCC should be separately discussed for technically resectable but oncologically unfavorable HCC and metastatic or invasive HCC beyond curative surgical treatments. The optimal downstaging treatment strategy for advanced HCC is awaited. Elucidation of preoperatively available factors that predict successful downstaging will allow the tailoring of promising initial treatments leading to conversion surgery. #"}],
"temperature": 0.0
}'

curl https://api.openai.com/v1/chat/completions \
-H "Content-Type: application/json" \
-H "Authorization: Bearer $OPEN_AI_KEY" \
-d '{
"model": "gpt-3.5-turbo",
"messages": [{"role": "user", "content": "Your goal is to summarize a text in one sentence of maximum 200 characters"}],
"temperature": 0.0
}'


                                                 
