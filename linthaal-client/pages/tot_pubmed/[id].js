import Layout from '../../components/layout';
import utilStyles from '../../styles/utils.module.css'

export default function SummarizationRequestView(response) {

  return <Layout>
    <h1 className={utilStyles.headingLg}>Summmarization Abstracts</h1>
    {response.data.sumAbsts ? (
            <ul>
              {response.data.sumAbsts.map((item, index) => (
                <li key={index}>
                  <h4>{item.sumTitle} (id: {item.id})</h4>
                  <p><strong>Date: </strong> {new Date(item.date).toLocaleString()}</p>
                  <p><strong>Abstract: </strong> {item.sumAbstract}</p>
              </li>
              ))}
            </ul>
          ) : (
            <p>Loading...</p>
          )}
  </Layout>;

}

export async function getServerSideProps(context) {
  const id = context?.params?.id;
  const res = await fetch(`http://localhost:7847/tot_pubmed/${id}`)

  const data = await res.json();
  return { props: { data } } 

}
