/* eslint-disable react/no-children-prop */
import Link from 'next/link';
import Layout, { siteTitle }  from '../../components/layout';
import utilStyles from '../../styles/utils.module.css'

export default function SummmarizationRequests({data}) {
  return (
    <>
      <Layout children>
        
        <h1 className={utilStyles.headingLg}>Summmarization Requests</h1>

        {data ? (
        <ul>
          {Object.keys(data.sumReqs).map((key, index) => (
            <li key={index}>
              <Link href={"/tot_pubmed/"+key}>{key}</Link>
              <ul>
                <li><strong>Search:</strong> {data.sumReqs[key].search}</li>
                <li><strong>Title Length:</strong> {data.sumReqs[key].titleLength}</li>
                <li><strong>Abstract Length:</strong> {data.sumReqs[key].abstractLength}</li>
              </ul>
            </li>
          ))}
        </ul>
      ) : (
        <p>Loading...</p>
      )}
      </Layout>
    </>
  );
}

  
export async function getServerSideProps() {
  // Fetch data from external API
  const res = await fetch(`http://localhost:7847/tot_pubmed`)
  const data = await res.json()
  // Pass data to the page via props
  return { props: { data } }
};
  